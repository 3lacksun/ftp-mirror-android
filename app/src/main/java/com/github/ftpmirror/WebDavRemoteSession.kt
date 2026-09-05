package com.github.ftpmirror

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.w3c.dom.Element
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class WebDavRemoteSession private constructor(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val basePath: String,
    private val username: String,
    private val password: String
) : RemoteSession {

    override fun list(path: String): List<RemoteEntry> {
        val normal = RemotePaths.normalise(path)
        val xml = """<?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:resourcetype />
                <d:getcontentlength />
                <d:getlastmodified />
              </d:prop>
            </d:propfind>
        """.trimIndent()

        val request = requestBuilder(normal)
            .header("Depth", "1")
            .method("PROPFIND", xml.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .build()

        execute(request).use { response ->
            if (response.code != 207 && !response.isSuccessful) {
                throw IOException("WebDAV PROPFIND failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("WebDAV returned an empty PROPFIND response")
            return parseMultiStatus(normal, body.byteStream())
        }
    }

    override fun ensureDir(path: String) {
        val normal = RemotePaths.normalise(path)
        if (normal == "/") return
        var current = ""
        normal.trim('/').split('/').filter { it.isNotBlank() }.forEach { part ->
            current += "/$part"
            val request = requestBuilder(current).method("MKCOL", null).build()
            execute(request).use { response ->
                if (!response.isSuccessful && response.code != 405) {
                    throw IOException("WebDAV MKCOL failed for $current: HTTP ${response.code}")
                }
            }
        }
    }

    override fun upload(path: String, input: InputStream) {
        val normal = RemotePaths.normalise(path)
        ensureDir(RemotePaths.parent(normal))
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun writeTo(sink: BufferedSink) {
                val source = input.source()
                while (true) {
                    val read = source.read(sink.buffer, 8_192L)
                    if (read == -1L) break
                    sink.emitCompleteSegments()
                }
            }
        }
        val request = requestBuilder(normal).put(body).build()
        execute(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("WebDAV PUT failed: HTTP ${response.code}")
            }
        }
    }

    override fun download(path: String, output: OutputStream) {
        val request = requestBuilder(RemotePaths.normalise(path)).get().build()
        execute(request).use { response ->
            if (!response.isSuccessful) {
                throw IOException("WebDAV GET failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("WebDAV returned an empty file body")
            body.byteStream().use { input -> input.copyTo(output) }
        }
    }

    override fun delete(path: String) {
        val request = requestBuilder(RemotePaths.normalise(path)).delete().build()
        execute(request).use { response ->
            if (!response.isSuccessful && response.code != 404) {
                throw IOException("WebDAV DELETE failed: HTTP ${response.code}")
            }
        }
    }

    override fun close() = Unit

    private fun requestBuilder(path: String): Request.Builder {
        val request = Request.Builder().url(url(path))
        if (username.isNotBlank()) {
            request.header("Authorization", Credentials.basic(username, password))
        }
        return request
    }

    private fun execute(request: Request) = client.newCall(request).execute()

    private fun url(path: String): HttpUrl {
        val normal = RemotePaths.normalise(path).trim('/')
        val builder = baseUrl.newBuilder()
        val joined = listOf(basePath.trim('/'), normal).filter { it.isNotBlank() }.joinToString("/")
        builder.encodedPath("/")
        if (joined.isNotBlank()) builder.addPathSegments(joined)
        return builder.build()
    }

    private fun parseMultiStatus(parent: String, input: InputStream): List<RemoteEntry> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            trySet("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySet("http://xml.org/sax/features/external-general-entities", false)
            trySet("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(input)
        val responses = document.getElementsByTagNameNS("*", "response")
        val out = mutableListOf<RemoteEntry>()
        for (index in 0 until responses.length) {
            val element = responses.item(index) as? Element ?: continue
            val href = element.text("href") ?: continue
            val hrefPath = try {
                URI(href).path ?: href
            } catch (_: Exception) {
                href
            }.trimEnd('/')
            val name = hrefPath.substringAfterLast('/').trim()
            if (name.isBlank()) continue

            val expected = RemotePaths.join(parent, name)
            if (RemotePaths.normalise(parent).trimEnd('/') == expected.trimEnd('/')) continue

            val typeNode = element.getElementsByTagNameNS("*", "resourcetype").item(0) as? Element
            val isDirectory = typeNode?.getElementsByTagNameNS("*", "collection")?.length?.let { it > 0 } ?: false
            val size = element.text("getcontentlength")?.trim()?.toLongOrNull() ?: 0L
            val modified = parseHttpDate(element.text("getlastmodified"))
            out += RemoteEntry(name, expected, isDirectory, if (isDirectory) 0L else size, modified)
        }
        return out.distinctBy { it.path }
    }

    private fun Element.text(localName: String): String? =
        getElementsByTagNameNS("*", localName).item(0)?.textContent

    private fun parseHttpDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
                isLenient = false
            }.parse(value.trim())?.time
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun connect(pair: SyncPair): WebDavRemoteSession {
            val parsed = if (pair.host.contains("://")) URI(pair.host) else null
            val scheme = parsed?.scheme?.lowercase(Locale.ROOT) ?: "https"
            val host = parsed?.host ?: pair.host
            val port = when {
                parsed?.port != null && parsed.port > 0 -> parsed.port
                parsed != null -> if (scheme == "http") 80 else 443
                else -> pair.port
            }
            val basePath = parsed?.path.orEmpty()
            val baseUrl = HttpUrl.Builder()
                .scheme(scheme)
                .host(host)
                .port(port)
                .build()

            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val session = WebDavRemoteSession(client, baseUrl, basePath, pair.username, pair.password)
            session.list(pair.remoteDir)
            return session
        }

        private fun DocumentBuilderFactory.trySet(feature: String, value: Boolean) {
            try {
                setFeature(feature, value)
            } catch (_: Exception) {
            }
        }
    }
}
