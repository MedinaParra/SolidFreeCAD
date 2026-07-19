#include "SolidGuiManager.h"

#include "SolidCommandBridge.h"

#include <QAction>
#include <QMessageBox>
#include <QToolBar>

#include <Gui/MainWindow.h>

namespace SolidFreeCAD
{

SolidGuiManager::SolidGuiManager(QObject* parent)
    : QObject(parent)
{}

SolidGuiManager::~SolidGuiManager()
{
    uninstall();
}

bool SolidGuiManager::install(Gui::MainWindow* mainWindow)
{
    if (!mainWindow) {
        return false;
    }

    if (ribbon_) {
        return true;
    }

    mainWindow_ = mainWindow;
    ribbon_ = new QToolBar(tr("SolidFreeCAD"), mainWindow_);
    ribbon_->setObjectName(QStringLiteral("SolidFreeCADRibbon"));
    ribbon_->setMovable(false);
    ribbon_->setFloatable(false);
    ribbon_->setToolButtonStyle(Qt::ToolButtonTextUnderIcon);

    addCommandAction(tr("New"), "Std_New");
    addCommandAction(tr("Open"), "Std_Open");
    addCommandAction(tr("Save"), "Std_Save");
    ribbon_->addSeparator();
    addCommandAction(tr("Undo"), "Std_Undo");
    addCommandAction(tr("Redo"), "Std_Redo");
    ribbon_->addSeparator();
    addCommandAction(tr("Fit all"), "Std_ViewFitAll");
    addCommandAction(tr("Isometric"), "Std_ViewAxonometric");

    mainWindow_->addToolBar(Qt::TopToolBarArea, ribbon_);
    ribbon_->show();
    return true;
}

void SolidGuiManager::uninstall()
{
    if (!ribbon_) {
        mainWindow_ = nullptr;
        return;
    }

    if (mainWindow_) {
        mainWindow_->removeToolBar(ribbon_);
    }

    ribbon_->deleteLater();
    ribbon_ = nullptr;
    mainWindow_ = nullptr;
}

bool SolidGuiManager::isInstalled() const
{
    return ribbon_ != nullptr;
}

QAction* SolidGuiManager::addCommandAction(const QString& label, const char* commandName)
{
    if (!ribbon_) {
        return nullptr;
    }

    QAction* action = ribbon_->addAction(label);
    const QString command = QString::fromLatin1(commandName);
    action->setData(command);
    action->setEnabled(SolidCommandBridge::isAvailable(commandName));

    connect(action, &QAction::triggered, this, [this, command]() {
        const QByteArray encoded = command.toLatin1();
        if (!SolidCommandBridge::invoke(encoded.constData()) && mainWindow_) {
            QMessageBox::warning(
                mainWindow_,
                tr("SolidFreeCAD"),
                tr("FreeCAD command is not available: %1").arg(command)
            );
        }
    });

    return action;
}

}  // namespace SolidFreeCAD
