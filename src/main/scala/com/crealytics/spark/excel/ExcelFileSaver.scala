package com.crealytics.spark.excel

import java.io.BufferedOutputStream

import com.norbitltd.spoiwo.model._
import com.norbitltd.spoiwo.natures.streaming.xlsx.Model2XlsxConversions._
import org.apache.hadoop.fs.{FSDataInputStream, FileSystem, Path}
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.spark.sql.{DataFrame, SaveMode}

import scala.collection.JavaConverters._

object ExcelFileSaver {
  final val DEFAULT_SHEET_NAME = "Sheet1"
  final val DEFAULT_DATE_FORMAT = "yy-m-d h:mm"
  final val DEFAULT_TIMESTAMP_FORMAT = "yyyy-mm-dd hh:mm:ss.000"
}

class ExcelFileSaver(
  fs: FileSystem,
  location: Path,
  dataFrame: DataFrame,
  saveMode: SaveMode,
  dataLocator: DataLocator,
  header: Boolean = true
) {
  def save(): Unit = {
    def sheet(workbook: SXSSFWorkbook) = {
      val headerRow = if (header) Some(dataFrame.schema.fields.map(_.name).toSeq) else None
      val dataRows = dataFrame
        .toLocalIterator()
        .asScala
        .map(_.toSeq)
      dataLocator.toSheet(headerRow, dataRows, workbook)
    }
    val fileAlreadyExists = fs.exists(location)
    def writeToWorkbook(workbook: SXSSFWorkbook): Unit = {
      Workbook(sheet(workbook)).writeToExisting(workbook)
      autoClose(new BufferedOutputStream(fs.create(location)))(workbook.write)
    }
    (fileAlreadyExists, saveMode) match {
      case (false, _) | (_, SaveMode.Overwrite) =>
        if (fileAlreadyExists) {
          fs.delete(location, true)
        }
        writeToWorkbook(new SXSSFWorkbook())
      case (true, SaveMode.ErrorIfExists) =>
        sys.error(s"path $location already exists.")
      case (true, SaveMode.Ignore) => ()
      case (true, SaveMode.Append) =>
        val inputStream: FSDataInputStream = fs.open(location)
        val workbook = new SXSSFWorkbook(new XSSFWorkbook(inputStream))
        inputStream.close()
        writeToWorkbook(workbook)
    }
  }

  def autoClose[A <: AutoCloseable, B](closeable: A)(fun: (A) => B): B = {
    try {
      fun(closeable)
    } finally {
      closeable.close()
    }
  }
}
