package com.sourceplusplus.plugin.intellij.settings.about

import javax.swing.*

/**
 * todo: description
 *
 * @version 0.2.5
 * @since 0.1.0
 * @author <a href="mailto:brandon@srcpl.us">Brandon Fergerson</a>
 */
class AboutDialog extends JDialog {

    private JPanel contentPane
    private JLabel websiteLabel
    private JLabel knowledgeBaseLabel
    private JLabel supportLabel
    private JLabel versionLabel

    AboutDialog() {
        setContentPane(contentPane)
        setModal(true)
    }

    @Override
    JPanel getContentPane() {
        return contentPane
    }

    JLabel getWebsiteLabel() {
        return websiteLabel
    }

    JLabel getKnowledgeBaseLabel() {
        return knowledgeBaseLabel
    }

    JLabel getSupportLabel() {
        return supportLabel
    }

    JLabel getVersionLabel() {
        return versionLabel
    }
}