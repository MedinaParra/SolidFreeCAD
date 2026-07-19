#pragma once

namespace SolidFreeCAD
{

class SolidCommandBridge final
{
public:
    SolidCommandBridge() = delete;

    static bool isAvailable(const char* commandName);
    static bool invoke(const char* commandName);
};

}  // namespace SolidFreeCAD
