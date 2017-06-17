/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception

import at.bitfire.dav4android.Constants
import okhttp3.Response
import okio.Buffer
import java.io.*

open class HttpException: Exception, Serializable {

    val status: Int
    val request: String?
    val response: String?

    constructor(message: String?): super(message) {
        status = -1
        request = null
        response = null
    }

    constructor(status: Int, message: String?): super("$status $message") {
        this.status = status

        request = null
        response = null
    }

    /**
     * Brings [response] into an readable format. Reads and closes the [response] body.
     */
    constructor(response: Response): super("${response.code()} ${response.message()}") {
        status = response.code()

        /* As we don't know the media type and character set of request and response body,
           only printable ASCII characters will be shown in clear text. Other octets will
           be shown as "[xx]" where xx is the hex value of the octet.
         */

        // format request
        val request = response.request()
        var formatted = StringBuilder()
        formatted.append(request.method()).append(" ").append(request.url().encodedPath()).append("\n")
        var headers = request.headers()
        for (name in headers.names())
            for (value in headers.values(name))
                formatted.append(name).append(": ").append(value).append("\n")
        request.body()?.let {
            try {
                val buffer = Buffer()
                it.writeTo(buffer)
                val baos = ByteArrayOutputStream()
                formatByteStream(buffer.inputStream(), baos)
                formatted.append("\n").append(baos.toString())
            } catch (e: IOException) {
                Constants.log.warning("Couldn't read request body")
            }
        }
        this.request = formatted.toString()

        // format response
        formatted = StringBuilder()
        formatted.append(response.protocol()).append(" ").append(response.code()).append(" ").append(response.message()).append("\n")
        headers = response.headers()
        for (name in headers.names())
            for (value in headers.values(name))
                formatted.append(name).append(": ").append(value).append("\n")

        response.body()?.use {
            try {
                val baos = ByteArrayOutputStream()
                formatByteStream(it.byteStream(), baos)
                formatted.append("\n").append(baos.toString())
            } catch(e: IOException) {
                Constants.log.warning("Couldn't read response body")
            }
        }
        this.response = formatted.toString()
    }

    private fun formatByteStream(input: InputStream, output: ByteArrayOutputStream) {
        OutputStreamWriter(output).use { writer ->
            while (input.available() != 0) {
                val b = input.read()
                when (b) {
                    '\r'.toInt()  -> writer.append("[CR]")
                    '\n'.toInt()  -> writer.append("[LF]\n")
                    in 0x20..0x7E -> writer.write(b)        // printable ASCII
                    else          -> writer.append("[${String.format("%02x", b)}]")
                }
            }
        }
    }

}
