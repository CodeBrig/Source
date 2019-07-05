package com.sourceplusplus.plugin.intellij.settings

import com.sourceplusplus.api.client.SourceCoreClient
import com.sourceplusplus.api.model.config.SourceEnvironmentConfig
import com.sourceplusplus.api.model.config.SourcePluginConfig
import com.sourceplusplus.plugin.PluginBootstrap
import com.sourceplusplus.plugin.intellij.IntelliJStartupActivity
import com.sourceplusplus.plugin.intellij.settings.application.ApplicationSettingsDialogWrapper
import com.sourceplusplus.plugin.intellij.settings.application.EditApplicationSettingsDialogWrapper
import org.apache.commons.lang.StringUtils
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.swing.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener

/**
 * todo: description
 *
 * @version 0.2.1
 * @since 0.1.0
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
class PluginSettingsDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(PluginSettingsDialog.class)
    private JPanel contentPane
    private JLabel connectionStatusLabel
    private JButton connectButton
    private JLabel applicationDetailsLabel
    private JButton editApplicationButton
    private JCheckBox agentPatcherEnabledCheckBox
    private ActionListener connectActionListener
    private SourceEnvironmentConfig currentEnvironment

    PluginSettingsDialog() {
        setContentPane(contentPane)
        setModal(true)
        connectButton.setEnabled(false)
        editApplicationButton.addActionListener(new AbstractAction() {
            @Override
            void actionPerformed(ActionEvent actionEvent) {
                EditApplicationSettingsDialogWrapper applicationSettings = new EditApplicationSettingsDialogWrapper(
                        IntelliJStartupActivity.currentProject)
                applicationSettings.createCenterPanel()
                applicationSettings.show()

                if (applicationSettings.getOkayAction() && !StringUtils.isEmpty(applicationSettings.getApplicationName())) {
                    applicationDetailsLabel.setText(String.format(
                            "<html>Application UUID: %s<br>Application name: %s</html>",
                            SourcePluginConfig.current.activeEnvironment.appUuid, applicationSettings.getApplicationName()))
                }
            }
        })

        if (PluginBootstrap.getSourcePlugin() != null) {
            updateConnectButton(true, SourcePluginConfig.current.activeEnvironment.coreClient)
        } else if (SourcePluginConfig.current.activeEnvironment == null) {
            updateConnectButton(false, null)
        } else {
            currentEnvironment = SourcePluginConfig.current.activeEnvironment
            SourceCoreClient newCoreClient = new SourceCoreClient(
                    SourcePluginConfig.current.activeEnvironment.getSppUrl())
            if (SourcePluginConfig.current.activeEnvironment.apiKey) {
                newCoreClient.setApiKey(SourcePluginConfig.current.activeEnvironment.apiKey)
            }
            newCoreClient.ping({
                if (it.succeeded()) {
                    updateConnectButton(true, newCoreClient)
                } else {
                    updateConnectButton(false, null)
                }
            })
        }
    }

    private void updateConnectButton(boolean isConnected, SourceCoreClient coreClient) {
        if (connectActionListener != null) {
            connectButton.removeActionListener(connectActionListener)
            connectActionListener = null
        }

        applicationDetailsLabel.setText("")
        if (isConnected) {
            if (PluginBootstrap.getSourcePlugin() == null && SourcePluginConfig.current.activeEnvironment?.appUuid) {
                coreClient.getApplication(SourcePluginConfig.current.activeEnvironment.appUuid, {
                    if (it.succeeded()) {
                        if (it.result().isPresent()) {
                            IntelliJStartupActivity.startSourcePlugin(coreClient)
                        }
                    } else {
                        log.error("Failed to get application", it.cause())
                    }
                })
            }

            connectionStatusLabel.setText("Status: Connected")
            connectButton.setEnabled(true)
            updateApplicationDetails(coreClient)
        } else {
            connectionStatusLabel.setText("Status: Not Connected")
            connectButton.setEnabled(false)
            editApplicationButton.setEnabled(false)
        }

        connectButton.addActionListener(connectActionListener = new AbstractAction() {
            @Override
            void actionPerformed(ActionEvent actionEvent) {
                ApplicationSettingsDialogWrapper applicationSettings = new ApplicationSettingsDialogWrapper(
                        IntelliJStartupActivity.currentProject, coreClient)
                applicationSettings.createCenterPanel()
                applicationSettings.show()

                if (applicationSettings.getOkayAction()) {
                    updateApplicationDetails(coreClient)
                }
            }
        })
    }

    private void updateApplicationDetails(SourceCoreClient coreClient) {
        if (SourcePluginConfig.current.activeEnvironment?.appUuid) {
            coreClient.getApplication(SourcePluginConfig.current.activeEnvironment.appUuid, {
                if (it.succeeded()) {
                    if (it.result().isPresent()) {
                        applicationDetailsLabel.setText(String.format(
                                "<html>Application UUID: %s<br>Application name: %s</html>",
                                it.result().get().appUuid(), it.result().get().appName()))
                        editApplicationButton.setEnabled(true)
                    } else {
                        applicationDetailsLabel.setText("")
                        editApplicationButton.setEnabled(false)
                    }
                    currentEnvironment = SourcePluginConfig.current.activeEnvironment
                } else {
                    log.error("Failed to get application", it.cause())
                }
            })
        } else {
            applicationDetailsLabel.setText("")
            editApplicationButton.setEnabled(false)
            if (SourcePluginConfig.current.activeEnvironment != currentEnvironment) {
                currentEnvironment = SourcePluginConfig.current.activeEnvironment
                if (SourcePluginConfig.current.activeEnvironment.coreClient) {
                    updateConnectButton(true, SourcePluginConfig.current.activeEnvironment.coreClient)
                } else {
                    updateConnectButton(false, null)
                }
            }
        }
    }

    void setData(@NotNull SourcePluginConfig config) {
        agentPatcherEnabledCheckBox.setSelected(config.agentPatcherEnabled)
    }

    void getData(@NotNull SourcePluginConfig data) {
        data.agentPatcherEnabled = agentPatcherEnabledCheckBox.isSelected()
    }

    boolean isModified(@NotNull SourcePluginConfig data) {
        if (SourcePluginConfig.current.activeEnvironment
                && SourcePluginConfig.current.activeEnvironment != currentEnvironment) {
            currentEnvironment = SourcePluginConfig.current.activeEnvironment

            if (SourcePluginConfig.current.activeEnvironment.coreClient) {
                updateConnectButton(true, SourcePluginConfig.current.activeEnvironment.coreClient)
            } else {
                SourceCoreClient newCoreClient = new SourceCoreClient(
                        SourcePluginConfig.current.activeEnvironment.getSppUrl())
                if (SourcePluginConfig.current.activeEnvironment.apiKey) {
                    newCoreClient.setApiKey(SourcePluginConfig.current.activeEnvironment.apiKey)
                }
                newCoreClient.ping({
                    if (it.succeeded()) {
                        updateConnectButton(true, newCoreClient)
                    } else {
                        updateConnectButton(false, null)
                    }
                })
            }
        }
        return agentPatcherEnabledCheckBox.isSelected() != data.agentPatcherEnabled
    }

    void setDataCustom(@NotNull SourcePluginConfig settings) {
        setData(settings)
    }
}
