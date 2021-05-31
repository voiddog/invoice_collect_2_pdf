package org.voiddog.kotlin.invoice_collect_2_pdf

import com.google.zxing.BinaryBitmap
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Float.min
import java.lang.Float.parseFloat

fun main(args: Array<String>) {
  val parser = ArgParser("invoice_collect_2_pdf")
  val inputDirParam by parser.option(ArgType.String, shortName = "i", description = "Input Invoice Directory")
    .required()
  val outFilePathParam by parser.option(ArgType.String, shortName = "o", description = "Output pdf file path")
    .required()

  parser.parse(args)
  val inputDir = File(inputDirParam)
  if (!inputDir.exists()) {
    throw RuntimeException("Input Directory: ${inputDir.absolutePath} not found.")
  }

  var outFile = File(outFilePathParam)
  if (outFile.isDirectory) {
    outFile = File(outFile, "out.pdf")
  }

  if (!outFile.parentFile.exists()) {
    outFile.parentFile.mkdirs()
  }

  val pdfFiles = collectPDF(inputDir)
  val doc = PDDocument()
  var price = 0.0f
  for (i in pdfFiles.indices step 2) {
    price += merge2PdfAsImgInPage(doc, pdfFiles[i], if (i + 1 < pdfFiles.size) pdfFiles[i + 1] else null)
  }
  doc.addPage(createPageWithText(doc, "Price: \$$price"))

  doc.save(outFile)
  doc.close()
  println("Invoice is save to ${outFile.absolutePath}")
}

data class Padding(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float
)

private fun createPageWithText(
  doc: PDDocument, content: String,
  padding: Padding = Padding(10.0f, 10.0f, 10.0f, 10.0f)
): PDPage {
  val page = PDPage(PDRectangle.A4)
  val contentStream = PDPageContentStream(doc, page)
  contentStream.beginText()
  contentStream.newLineAtOffset(padding.left, padding.top)
  contentStream.setFont(PDType1Font.HELVETICA_BOLD, 20.0f)
  contentStream.showText(content)
  contentStream.endText()
  contentStream.close()
  return page
}

private fun renderPage(pdfFile: File, index: Int = 0): BufferedImage {
  return PDFRenderer(Loader.loadPDF(pdfFile)).renderImage(index, 3.0f)
}

private fun merge2PdfAsImgInPage(
  doc: PDDocument, pdfFileA: File?, pdfFileB: File?,
  padding: Padding = Padding(10.0f, 10.0f, 10.0f, 10.0f)
): Float{
  var price = 0.0f
  val page = PDPage(PDRectangle.A4)
  val box = page.mediaBox
  val width = box.width - padding.left - padding.right
  val height = box.height / 2.0f - padding.top - padding.bottom
  val contentStream = PDPageContentStream(doc, page)

  var img = if (pdfFileA == null) null else renderPage(pdfFileA)
  if (img != null) {
    price += getPriceFromImage(img, pdfFileA?.path ?: "")
    val imgObj = JPEGFactory.createFromImage(doc, img)
    drawImage2Box(PDRectangle(padding.left, padding.top, width, height), contentStream, imgObj)
  }

  img = if (pdfFileB == null) null else renderPage(pdfFileB)
  if (img != null) {
    price += getPriceFromImage(img, pdfFileB?.path ?: "")
    val imgObj = JPEGFactory.createFromImage(doc, img)
    drawImage2Box(PDRectangle(padding.left, padding.top + box.height / 2, width, height), contentStream, imgObj)
  }

  contentStream.close()
  doc.addPage(page)
  return price
}

private fun getPriceFromImage(bufferedImage: BufferedImage, pathFrom: String): Float {
  return try {
    val reader = QRCodeReader()
    val content = reader.decode(BinaryBitmap(HybridBinarizer(BufferedImageLuminanceSource(bufferedImage)))).text
    val contentList = content.split(",")
    parseFloat(contentList[4])
  } catch (e: Exception) {
    println("Can not get price from: $pathFrom")
    0f
  }
}

private fun drawImage2Box(rect: PDRectangle, contentStream: PDPageContentStream, imgObj: PDImageXObject) {
  val scale = min(rect.width / imgObj.width, rect.height / imgObj.height)
  val scaleWidth = imgObj.width * scale
  val scaleHeight = imgObj.height * scale
  val startX = rect.width - scaleWidth + rect.lowerLeftX
  val startY = rect.height - scaleHeight + rect.lowerLeftY
  contentStream.drawImage(imgObj, startX, startY, scaleWidth, scaleHeight)
}

private fun collectPDF(dir: File): List<File> {
  return dir.listFiles { file ->
    try {
      Loader.loadPDF(file)
      true
    } catch (e: Exception) {
      false
    }
  }?.toList() ?: ArrayList()
}