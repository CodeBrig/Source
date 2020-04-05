package com.sourceplusplus.plugin.intellij.settings.application

import com.sourceplusplus.api.model.application.SourceApplication
import com.sourceplusplus.api.model.config.SourceAgentConfig

import javax.swing.*

/**
 * todo: description
 *
 * @version 0.2.5
 * @since 0.1.0
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
class EditApplicationSettingsDialog extends JDialog {

    private JPanel contentPane
    private JTextField applicationNameTextField
    private JTextField applicationDomainTextField
    private JSpinner maxSpanCountSpinner

    EditApplicationSettingsDialog() {
        setContentPane(contentPane)
        setModal(true)
        maxSpanCountSpinner.setValue(300)
    }

    void setCurrentProject(SourceApplication sourceApplication) {
        applicationNameTextField.setText(sourceApplication.appName())
        SourceAgentConfig agentConfig = sourceApplication.agentConfig()
        if (agentConfig != null) {
            if (agentConfig.packages != null) {
                StringBuilder sb = new StringBuilder()
                for (int i = 0; i < agentConfig.packages.size(); i++) {
                    sb.append(agentConfig.packages.get(i))
                    if ((i + 1) < agentConfig.packages.size()) {
                        sb.append(",")
                    }
                }
                applicationDomainTextField.setText(sb.toString())
            }
            if (agentConfig.spanLimitPerSegment != null) {
                maxSpanCountSpinner.setValue(agentConfig.spanLimitPerSegment)
            }
        }
    }

    String getApplicationName() {
        return applicationNameTextField.getText()
    }

    String getApplicationDomain() {
        return applicationDomainTextField.getText()
    }

    int getMaxSpanCount() {
        return (int) maxSpanCountSpinner.getValue()
    }

    @Override
    JPanel getContentPane() {
        return contentPane
    }
}