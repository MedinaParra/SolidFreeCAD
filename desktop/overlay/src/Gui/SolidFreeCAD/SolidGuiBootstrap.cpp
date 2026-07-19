#include "SolidGuiBootstrap.h"

#include "SolidGuiManager.h"

#include <memory>

namespace SolidFreeCAD
{
namespace
{
std::unique_ptr<SolidGuiManager> guiManager;
}

void installGui(Gui::MainWindow* mainWindow)
{
    if (!guiManager) {
        guiManager = std::make_unique<SolidGuiManager>();
    }

    guiManager->install(mainWindow);
}

void uninstallGui()
{
    guiManager.reset();
}

}  // namespace SolidFreeCAD
