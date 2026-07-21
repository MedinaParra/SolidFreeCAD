package com.medinaparra.freecadandroid.io

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeCadArchiveRoundTripTest {
    @Test
    fun preservesUnknownWorkbenchEntriesAndEditsUniversalMetadata() {
        val root = createTempDir(prefix = "solidfreecad-fcstd-")
        try {
            val source = File(root, "source.FCStd")
            val shapeBytes = "DBRep_DrawableShape\nCASCADE Topology V3\n".toByteArray()
            val femBytes = "<FemData opaque=\"true\">keep-me</FemData>".toByteArray()
            writeArchive(source, shapeBytes, femBytes)

            val extracted = FreeCadArchiveReader.extract(
                archiveFile = source,
                outputRoot = File(root, "extract"),
                displayName = "source.FCStd"
            )

            assertEquals("Industrial sample", extracted.documentName)
            assertEquals(2, extracted.objects.size)
            assertEquals(2, extracted.shapeObjects.size)
            assertEquals("PartDesign::Feature", extracted.objects.first().typeId)
            assertEquals("Body", extracted.objects.last().linkedObjectName)
            assertEquals(extracted.objects.first().brepFile, extracted.objects.last().brepFile)
            assertEquals("workbench-specific-value", extracted.objects.first().propertyValues["CustomWorkbenchData"])

            val updated = extracted.objects.map { record ->
                when (record.name) {
                    "Body" -> record.copy(
                        label = "Body edited on Android",
                        visible = false,
                        placement = record.placement.copy(x = 12.5, y = -3.0, z = 8.25)
                    )
                    else -> record
                }
            }
            val destination = File(root, "roundtrip.FCStd")
            FreeCadArchiveWriter.write(extracted, updated, destination)

            ZipFile(source).use { before ->
                ZipFile(destination).use { after ->
                    val beforeNames = before.entries().asSequence().map { it.name }.toList()
                    val afterNames = after.entries().asSequence().map { it.name }.toList()
                    assertEquals(beforeNames, afterNames)
                    assertArrayEquals(shapeBytes, after.getInputStream(after.getEntry("Body.brp")).readBytes())
                    assertArrayEquals(femBytes, after.getInputStream(after.getEntry("Fem/Analysis.xml")).readBytes())
                    val xml = after.getInputStream(after.getEntry("Document.xml")).reader().readText()
                    assertTrue(xml.contains("Body edited on Android"))
                    assertTrue(xml.contains("workbench-specific-value"))
                    assertTrue(xml.contains("Px=\"12.5000000000000000\""))
                    assertTrue(xml.contains("Py=\"-3.0000000000000000\""))
                }
            }

            val reopened = FreeCadArchiveReader.extract(
                archiveFile = destination,
                outputRoot = File(root, "reopen"),
                displayName = "roundtrip.FCStd"
            )
            val body = reopened.objects.first { it.name == "Body" }
            assertEquals("Body edited on Android", body.label)
            assertFalse(body.visible)
            assertEquals(12.5, body.placement.x, 0.0)
            assertEquals(-3.0, body.placement.y, 0.0)
            assertEquals(8.25, body.placement.z, 0.0)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeArchive(destination: File, shapeBytes: ByteArray, femBytes: ByteArray) {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document SchemaVersion="4" ProgramVersion="1.1.1">
              <Properties>
                <Property name="Label" type="App::PropertyString"><String value="Industrial sample"/></Property>
                <Property name="LastModifiedDate" type="App::PropertyString"><String value="2026-07-20T00:00:00Z"/></Property>
              </Properties>
              <Objects Count="2">
                <Object type="PartDesign::Feature" name="Body" id="1"/>
                <Object type="App::Link" name="BodyLink" id="2"/>
              </Objects>
              <ObjectData Count="2">
                <Object name="Body">
                  <Properties Count="5">
                    <Property name="Label" type="App::PropertyString"><String value="Body original"/></Property>
                    <Property name="Visibility" type="App::PropertyBool"><Bool value="true"/></Property>
                    <Property name="Placement" type="App::PropertyPlacement"><PropertyPlacement Px="0" Py="0" Pz="0" Q0="0" Q1="0" Q2="0" Q3="1"/></Property>
                    <Property name="Shape" type="Part::PropertyPartShape"><Part file="Body.brp"/></Property>
                    <Property name="CustomWorkbenchData" type="App::PropertyString"><String value="workbench-specific-value"/></Property>
                  </Properties>
                </Object>
                <Object name="BodyLink">
                  <Properties Count="3">
                    <Property name="Label" type="App::PropertyString"><String value="Body link"/></Property>
                    <Property name="LinkedObject" type="App::PropertyXLink"><XLink file="" name="Body"/></Property>
                    <Property name="Placement" type="App::PropertyPlacement"><PropertyPlacement Px="1" Py="2" Pz="3" Q0="0" Q1="0" Q2="0" Q3="1"/></Property>
                  </Properties>
                </Object>
              </ObjectData>
            </Document>
        """.trimIndent().toByteArray()
        val guiXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <GuiDocument>
              <ViewProviderData Count="2">
                <ViewProvider name="Body"><Properties><Property name="Visibility"><Bool value="true"/></Property></Properties></ViewProvider>
                <ViewProvider name="BodyLink"><Properties><Property name="Visibility"><Bool value="true"/></Property></Properties></ViewProvider>
              </ViewProviderData>
            </GuiDocument>
        """.trimIndent().toByteArray()

        ZipOutputStream(destination.outputStream().buffered()).use { zip ->
            listOf(
                "Document.xml" to documentXml,
                "GuiDocument.xml" to guiXml,
                "Body.brp" to shapeBytes,
                "Fem/Analysis.xml" to femBytes
            ).forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
