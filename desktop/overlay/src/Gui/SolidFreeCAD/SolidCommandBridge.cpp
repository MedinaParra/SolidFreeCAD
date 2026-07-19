#include "SolidCommandBridge.h"

#include <Gui/Application.h>
#include <Gui/Command.h>

namespace SolidFreeCAD
{

bool SolidCommandBridge::isAvailable(const char* commandName)
{
    if (!commandName || !*commandName || !Gui::Application::Instance) {
        return false;
    }

    auto& manager = Gui::Application::Instance->commandManager();
    return manager.getCommandByName(commandName) != nullptr;
}

bool SolidCommandBridge::invoke(const char* commandName)
{
    if (!isAvailable(commandName)) {
        return false;
    }

    Gui::Application::Instance->commandManager().runCommandByName(commandName);
    return true;
}

}  // namespace SolidFreeCAD
