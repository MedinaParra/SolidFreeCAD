#pragma once

namespace Gui
{
class MainWindow;
}

namespace SolidFreeCAD
{

void installGui(Gui::MainWindow* mainWindow);
void uninstallGui();

}  // namespace SolidFreeCAD
