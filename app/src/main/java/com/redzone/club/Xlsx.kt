package com.redzone.club

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** كاتب ملفات Excel (xlsx) بدون أي مكتبات خارجية. */
class XCell(val v: Any?, val s: Int)

class XSheet(val name: String, val widths: List<Double>, val freeze: Boolean = false) {
    val rows = ArrayList<List<XCell?>>()
    fun add(vararg cells: XCell?) { rows.add(cells.toList()) }
}

object X {
    // أنماط الخلايا
    const val PLAIN = 0
    const val HEAD = 1
    const val TXT = 2
    const val NUM = 3
    const val TITLE = 4
    const val TOTL = 5
    const val TOTN = 6

    fun t(s: String, st: Int = TXT) = XCell(s, st)
    fun n(d: Double, st: Int = NUM) = XCell(d, st)
    fun n(d: Long, st: Int = NUM) = XCell(d.toDouble(), st)
    fun n(d: Int, st: Int = NUM) = XCell(d.toDouble(), st)

    private const val STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"4\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"16\"/><color rgb=\"FFE5192D\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE5192D\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF3F4F6\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style=\"thin\"><color rgb=\"FFD1D5DB\"/></left><right style=\"thin\"><color rgb=\"FFD1D5DB\"/></right><top style=\"thin\"><color rgb=\"FFD1D5DB\"/></top><bottom style=\"thin\"><color rgb=\"FFD1D5DB\"/></bottom><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"7\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf><xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf><xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/><xf numFmtId=\"0\" fontId=\"3\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\"/></xf><xf numFmtId=\"3\" fontId=\"3\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (ch in s) {
            if (ch.code < 32 && ch != '\t' && ch != '\n' && ch != '\r') continue
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun col(n0: Int): String {
        var n = n0 + 1
        var s = ""
        while (n > 0) { val r = (n - 1) % 26; s = (65 + r).toChar() + s; n = (n - 1) / 26 }
        return s
    }

    private fun sheetName(n0: String): String {
        var n = n0
        for (c in charArrayOf('[', ']', ':', '*', '?', '/', '\\')) n = n.replace(c, ' ')
        n = n.trim()
        if (n.isEmpty()) n = "Sheet"
        if (n.length > 31) n = n.substring(0, 31)
        return n
    }

    private fun num(d0: Double): String {
        val d = if (d0.isNaN() || d0.isInfinite()) 0.0 else d0
        return if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()
    }

    private fun sheetXml(sh: XSheet): String {
        val o = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\"")
        if (sh.freeze) o.append("><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        else o.append("/></sheetViews>")
        o.append("<sheetFormatPr defaultRowHeight=\"15\"/><cols>")
        sh.widths.forEachIndexed { i, w -> o.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>") }
        o.append("</cols><sheetData>")
        sh.rows.forEachIndexed { r, cells ->
            o.append("<row r=\"${r + 1}\">")
            cells.forEachIndexed { c, cell ->
                if (cell != null) {
                    val rf = col(c) + (r + 1)
                    val v = cell.v
                    if (v == null || (v is String && v.isEmpty())) o.append("<c r=\"$rf\" s=\"${cell.s}\"/>")
                    else if (v is String) o.append("<c r=\"$rf\" s=\"${cell.s}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>")
                    else o.append("<c r=\"$rf\" s=\"${cell.s}\"><v>${num((v as Number).toDouble())}</v></c>")
                }
            }
            o.append("</row>")
        }
        o.append("</sheetData></worksheet>")
        return o.toString()
    }

    private fun put(z: ZipOutputStream, name: String, content: String) {
        z.putNextEntry(ZipEntry(name))
        z.write(content.toByteArray(Charsets.UTF_8))
        z.closeEntry()
    }

    fun build(sheets: List<XSheet>): ByteArray {
        val n = sheets.size
        val ct = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
        for (i in 0 until n) ct.append("<Override PartName=\"/xl/worksheets/sheet${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
        ct.append("</Types>")
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
        val wb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
        val wr = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        for (i in 0 until n) {
            wb.append("<sheet name=\"${esc(sheetName(sheets[i].name))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>")
            wr.append("<Relationship Id=\"rId${i + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i + 1}.xml\"/>")
        }
        wb.append("</sheets></workbook>")
        wr.append("<Relationship Id=\"rId${n + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            put(z, "[Content_Types].xml", ct.toString())
            put(z, "_rels/.rels", rels)
            put(z, "xl/workbook.xml", wb.toString())
            put(z, "xl/_rels/workbook.xml.rels", wr.toString())
            put(z, "xl/styles.xml", STYLES)
            for (i in 0 until n) put(z, "xl/worksheets/sheet${i + 1}.xml", sheetXml(sheets[i]))
        }
        return bos.toByteArray()
    }
}
