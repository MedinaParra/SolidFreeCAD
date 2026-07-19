set(SolidFreeCADGui_SRCS
    SolidCommandBridge.cpp
    SolidCommandBridge.h
    SolidGuiBootstrap.cpp
    SolidGuiBootstrap.h
    SolidGuiManager.cpp
    SolidGuiManager.h
)

# This directory is intended to be added from src/Gui/CMakeLists.txt after the
# FreeCADGui target has been created.
target_sources(FreeCADGui PRIVATE ${SolidFreeCADGui_SRCS})

target_include_directories(FreeCADGui PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

source_group("SolidFreeCAD" FILES ${SolidFreeCADGui_SRCS})
