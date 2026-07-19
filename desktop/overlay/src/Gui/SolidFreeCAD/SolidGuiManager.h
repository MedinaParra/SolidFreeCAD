#pragma once

#include <QObject>
#include <QString>

class QAction;
class QToolBar;

namespace Gui
{
class MainWindow;
}

namespace SolidFreeCAD
{

class SolidGuiManager final : public QObject
{
public:
    explicit SolidGuiManager(QObject* parent = nullptr);
    ~SolidGuiManager() override;

    bool install(Gui::MainWindow* mainWindow);
    void uninstall();
    bool isInstalled() const;

private:
    QAction* addCommandAction(const QString& label, const char* commandName);

    Gui::MainWindow* mainWindow_ = nullptr;
    QToolBar* ribbon_ = nullptr;
};

}  // namespace SolidFreeCAD
