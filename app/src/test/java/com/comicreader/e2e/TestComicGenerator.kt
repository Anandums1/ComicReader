package com.comicreader.e2e

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Programmatic test data generator for synthetic comic archives and documents.
 * Generates valid CBZ (ZIP), CBR (RAR/RAR5 headers), PDF, and EPUB files
 * completely in memory and local temp files without external network or physical assets.
 */
object TestComicGenerator {

    /**
     * Generates valid PNG image bytes in memory using pure Kotlin standard library PNG encoding.
     */
    fun generateValidImageBytes(
        width: Int = 120,
        height: Int = 160,
        colorRgb: Int = 0x2196F3, // Default Material Blue
        label: String? = null,
        format: String = "png"
    ): ByteArray {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val r = ((colorRgb shr 16) and 0xFF).toByte()
        val g = ((colorRgb shr 8) and 0xFF).toByte()
        val b = (colorRgb and 0xFF).toByte()
        val a = 0xFF.toByte()

        // Uncompressed image scanlines: each row has 1 filter byte (0) + w * 4 bytes RGBA
        val uncompressedBytes = ByteArray(h * (1 + w * 4))
        var offset = 0
        for (y in 0 until h) {
            uncompressedBytes[offset++] = 0 // Filter type: None
            for (x in 0 until w) {
                uncompressedBytes[offset++] = r
                uncompressedBytes[offset++] = g
                uncompressedBytes[offset++] = b
                uncompressedBytes[offset++] = a
            }
        }

        // Compress scanlines with Deflater
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(uncompressedBytes)
        deflater.finish()
        val compressedBuffer = ByteArray(uncompressedBytes.size + 1024)
        val compressedSize = deflater.deflate(compressedBuffer)
        deflater.end()

        val idatData = compressedBuffer.copyOf(compressedSize)

        // Write PNG stream
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 1. Signature
        dos.write(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()))

        // Helper to write chunk
        fun writeChunk(type: String, data: ByteArray) {
            dos.writeInt(data.size)
            val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
            dos.write(typeBytes)
            dos.write(data)
            val crc = CRC32()
            crc.update(typeBytes)
            crc.update(data)
            dos.writeInt(crc.value.toInt())
        }

        // 2. IHDR
        val ihdrData = ByteArray(13)
        val ihdrBuf = ByteBuffer.wrap(ihdrData)
        ihdrBuf.putInt(w)
        ihdrBuf.putInt(h)
        ihdrBuf.put(8.toByte()) // bit depth
        ihdrBuf.put(6.toByte()) // color type: RGBA
        ihdrBuf.put(0.toByte()) // compression
        ihdrBuf.put(0.toByte()) // filter
        ihdrBuf.put(0.toByte()) // interlace
        writeChunk("IHDR", ihdrData)

        // 3. IDAT
        writeChunk("IDAT", idatData)

        // 4. IEND
        writeChunk("IEND", ByteArray(0))

        dos.flush()
        return baos.toByteArray()
    }

    /**
     * Synthesizes a valid CBZ (ZIP) file containing ordered comic pages and optional ComicInfo.xml metadata.
     */
    fun createMockCbz(
        targetFile: File,
        pageCount: Int = 3,
        title: String? = "Synthetic Test Comic",
        series: String? = "Test Series",
        writer: String? = "Test Writer",
        customFilenames: List<String>? = null,
        includeExtraFiles: Boolean = false,
        imageFormat: String = "png"
    ): File {
        targetFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            val filenames = customFilenames ?: (1..pageCount).map { i ->
                String.format("page%03d.%s", i, imageFormat)
            }

            // Write image pages
            filenames.forEachIndexed { index, name ->
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                val imgBytes = generateValidImageBytes(
                    width = 200,
                    height = 300,
                    colorRgb = 0x102030 + (index * 0x05080A),
                    label = "Page ${index + 1}",
                    format = imageFormat
                )
                zos.write(imgBytes)
                zos.closeEntry()
            }

            // Write ComicInfo.xml if metadata provided
            if (title != null) {
                val xml = buildComicInfoXml(title, series, writer, pageCount)
                val xmlEntry = ZipEntry("ComicInfo.xml")
                zos.putNextEntry(xmlEntry)
                zos.write(xml.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            // Include OS metadata artifacts if requested (to test filtering)
            if (includeExtraFiles) {
                val dsStore = ZipEntry(".DS_Store")
                zos.putNextEntry(dsStore)
                zos.write("macOS metadata".toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                val macOsx = ZipEntry("__MACOSX/._page001.png")
                zos.putNextEntry(macOsx)
                zos.write("resource fork".toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()

                val readme = ZipEntry("readme.txt")
                zos.putNextEntry(readme)
                zos.write("Scanned by ComicFan".toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }
        return targetFile
    }

    /**
     * Generates a mock CBR file.
     * Supports standard RAR header, RAR5 magic bytes, or corrupt bytes.
     */
    fun createMockCbr(
        targetFile: File,
        isRar5: Boolean = false,
        corrupt: Boolean = false
    ): File {
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { fos ->
            if (corrupt) {
                fos.write("CORRUPT_RAR_HEADER_NOT_VALID".toByteArray(StandardCharsets.UTF_8))
            } else if (isRar5) {
                // RAR 5.0 Signature: 0x52 0x61 0x72 0x21 0x1A 0x07 0x01 0x00
                val rar5Header = byteArrayOf(
                    0x52.toByte(), 0x61.toByte(), 0x72.toByte(), 0x21.toByte(),
                    0x1A.toByte(), 0x07.toByte(), 0x01.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()
                )
                fos.write(rar5Header)
            } else {
                // Standard RAR 4.x Signature: 0x52 0x61 0x72 0x21 0x1A 0x07 0x00
                val rar4Header = byteArrayOf(
                    0x52.toByte(), 0x61.toByte(), 0x72.toByte(), 0x21.toByte(),
                    0x1A.toByte(), 0x07.toByte(), 0x00.toByte(),
                    0xCF.toByte(), 0x90.toByte(), 0x73.toByte(), 0x00.toByte(),
                    0x00.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x00.toByte()
                )
                fos.write(rar4Header)
            }
        }
        return targetFile
    }

    /**
     * Synthesizes a valid minimal PDF 1.4 document conforming strictly to ISO 32000-1 / PDF 1.4.
     */
    fun createMockPdf(
        targetFile: File,
        pageCount: Int = 3,
        title: String? = "Synthetic PDF Comic",
        corrupt: Boolean = false
    ): File {
        targetFile.parentFile?.mkdirs()
        if (corrupt) {
            targetFile.writeText("NOT_A_VALID_PDF_DOCUMENT_GARBAGE")
            return targetFile
        }

        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")
        sb.append("%\u00E2\u00E3\u00CF\u00D3\n") // Binary marker

        val offsets = mutableListOf<Int>()

        // 1 0 obj: Catalog
        offsets.add(sb.length)
        sb.append("1 0 obj\n")
        sb.append("<< /Type /Catalog /Pages 2 0 R >>\n")
        sb.append("endobj\n")

        // Page obj refs
        val pageRefs = (1..pageCount).joinToString(" ") { "${it + 2} 0 R" }

        // 2 0 obj: Pages
        offsets.add(sb.length)
        sb.append("2 0 obj\n")
        sb.append("<< /Type /Pages /Kids [ $pageRefs ] /Count $pageCount >>\n")
        sb.append("endobj\n")

        // Page objects
        for (i in 1..pageCount) {
            offsets.add(sb.length)
            sb.append("${i + 2} 0 obj\n")
            sb.append("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\n")
            sb.append("endobj\n")
        }

        // Info dict
        val infoObjNum = pageCount + 3
        offsets.add(sb.length)
        sb.append("$infoObjNum 0 obj\n")
        sb.append("<< /Title (${title ?: "Untitled"}) /Producer (TestComicGenerator) >>\n")
        sb.append("endobj\n")

        // Cross-reference table
        val xrefStart = sb.length
        sb.append("xref\n")
        sb.append("0 ${infoObjNum + 1}\n")
        sb.append("0000000000 65535 f \n")
        for (offset in offsets) {
            sb.append(String.format("%010d 00000 n \n", offset))
        }

        // Trailer
        sb.append("trailer\n")
        sb.append("<< /Size ${infoObjNum + 1} /Root 1 0 R /Info $infoObjNum 0 R >>\n")
        sb.append("startxref\n")
        sb.append("$xrefStart\n")
        sb.append("%%EOF\n")

        targetFile.writeBytes(sb.toString().toByteArray(StandardCharsets.US_ASCII))
        return targetFile
    }

    /**
     * Synthesizes a valid EPUB archive containing container.xml, content.opf, spine, and images/XHTML.
     */
    fun createMockEpub(
        targetFile: File,
        pageCount: Int = 3,
        title: String = "Synthetic EPUB Comic",
        fixedLayout: Boolean = true,
        corrupt: Boolean = false,
        missingContainer: Boolean = false
    ): File {
        targetFile.parentFile?.mkdirs()
        if (corrupt) {
            targetFile.writeText("GARBAGE_NOT_A_ZIP_EPUB")
            return targetFile
        }

        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            // 1. mimetype (must be first and uncompressed per EPUB spec)
            val mimeEntry = ZipEntry("mimetype")
            zos.putNextEntry(mimeEntry)
            zos.write("application/epub+zip".toByteArray(StandardCharsets.US_ASCII))
            zos.closeEntry()

            // 2. META-INF/container.xml
            if (!missingContainer) {
                val containerEntry = ZipEntry("META-INF/container.xml")
                zos.putNextEntry(containerEntry)
                val containerXml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
                zos.write(containerXml.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            // 3. Image assets
            val imageNames = (1..pageCount).map { "page${it}.png" }
            for ((index, imgName) in imageNames.withIndex()) {
                val imgEntry = ZipEntry("OEBPS/images/$imgName")
                zos.putNextEntry(imgEntry)
                val imgBytes = generateValidImageBytes(
                    width = 200,
                    height = 300,
                    colorRgb = 0xAA0000 + (index * 0x001122),
                    label = "EPUB Page ${index + 1}"
                )
                zos.write(imgBytes)
                zos.closeEntry()
            }

            // 4. Fixed-layout XHTML pages if requested
            if (fixedLayout) {
                for ((index, imgName) in imageNames.withIndex()) {
                    val pageEntry = ZipEntry("OEBPS/pages/page${index + 1}.xhtml")
                    zos.putNextEntry(pageEntry)
                    val xhtml = """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Page ${index + 1}</title></head>
<body>
  <div style="text-align:center;">
    <img src="../images/$imgName" alt="Page ${index + 1}" />
  </div>
</body>
</html>"""
                    zos.write(xhtml.toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()
                }
            }

            // 5. OEBPS/content.opf
            val opfEntry = ZipEntry("OEBPS/content.opf")
            zos.putNextEntry(opfEntry)
            val opfXml = buildOpfXml(title, pageCount, fixedLayout, imageNames)
            zos.write(opfXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        return targetFile
    }

    /**
     * Creates an empty 0-byte file.
     */
    fun createEmptyFile(targetFile: File): File {
        targetFile.parentFile?.mkdirs()
        targetFile.writeBytes(ByteArray(0))
        return targetFile
    }

    /**
     * Creates a corrupt file of any extension.
     */
    fun createCorruptFile(targetFile: File, extension: String): File {
        targetFile.parentFile?.mkdirs()
        targetFile.writeText("CORRUPTED_FILE_DATA_${System.currentTimeMillis()}")
        return targetFile
    }

    private fun buildComicInfoXml(
        title: String,
        series: String?,
        writer: String?,
        pageCount: Int
    ): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<ComicInfo xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <Title>$title</Title>
  <Series>${series ?: "Default Series"}</Series>
  <Number>1</Number>
  <Volume>1</Volume>
  <Summary>Synthetic comic generated for E2E testing.</Summary>
  <Writer>${writer ?: "Comic Author"}</Writer>
  <Penciller>Comic Artist</Penciller>
  <Genre>Action / Adventure</Genre>
  <PageCount>$pageCount</PageCount>
  <Year>2026</Year>
  <Month>10</Month>
</ComicInfo>"""
    }

    private fun buildOpfXml(
        title: String,
        pageCount: Int,
        fixedLayout: Boolean,
        imageNames: List<String>
    ): String {
        val manifestItems = StringBuilder()
        val spineItems = StringBuilder()

        // Images in manifest
        imageNames.forEachIndexed { idx, name ->
            manifestItems.append("    <item id=\"img-${idx + 1}\" href=\"images/$name\" media-type=\"image/png\"/>\n")
        }

        // Spine items
        if (fixedLayout) {
            for (i in 1..pageCount) {
                manifestItems.append("    <item id=\"page-$i\" href=\"pages/page$i.xhtml\" media-type=\"application/xhtml+xml\"/>\n")
                spineItems.append("    <itemref idref=\"page-$i\"/>\n")
            }
        } else {
            for (i in 1..pageCount) {
                spineItems.append("    <itemref idref=\"img-$i\"/>\n")
            }
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="pub-id" version="3.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>$title</dc:title>
    <dc:creator>Test Generator</dc:creator>
    <dc:language>en</dc:language>
    <meta name="cover" content="img-1"/>
  </metadata>
  <manifest>
$manifestItems  </manifest>
  <spine>
$spineItems  </spine>
</package>"""
    }
}
