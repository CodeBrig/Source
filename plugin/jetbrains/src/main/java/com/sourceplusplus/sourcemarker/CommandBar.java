package com.sourceplusplus.sourcemarker;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import com.sourceplusplus.marker.source.mark.inlay.InlayMark;
import com.sourceplusplus.sourcemarker.command.AutocompleteFieldRow;
import com.sourceplusplus.sourcemarker.command.CommandAction;
import com.sourceplusplus.sourcemarker.command.CommandBarController;
import com.sourceplusplus.sourcemarker.status.util.AutocompleteField;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sourceplusplus.sourcemarker.status.util.ViewUtils.addRecursiveMouseListener;

public class CommandBar extends JPanel implements VisibleAreaListener {

    private final Function<String, List<AutocompleteFieldRow>> lookup = text -> Arrays.stream(CommandAction.values())
            .filter(v -> !text.isEmpty()
                    && v.getText().toLowerCase().contains(text.toLowerCase().replace("/", ""))
                    && text.startsWith("/")
                    && !v.getText().equalsIgnoreCase(text)
            ).collect(Collectors.toList());

    private final Editor editor;
    private final InlayMark inlayMark;
    private boolean disposed = false;

    public CommandBar(Editor editor, InlayMark inlayMark) {
        this.editor = editor;
        this.inlayMark = inlayMark;

        initComponents();
        setupComponents();
        setCursor(Cursor.getDefaultCursor());
    }

    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
        textField1.hideAutocompletePopup();
    }

    public void focus() {
        textField1.grabFocus();
        textField1.requestFocusInWindow();
    }

    private void setupComponents() {
        textField1.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == KeyEvent.VK_TAB) {
                    //ignore tab; handled by auto-complete
                    e.consume();
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == KeyEvent.VK_ESCAPE) {
                    dispose();
                } else if (e.getKeyChar() == KeyEvent.VK_ENTER) {
                    String autoCompleteText = textField1.getSelectedText();
                    if (autoCompleteText != null) {
                        CommandBarController.INSTANCE.handleCommandInput(autoCompleteText, editor);
                    } else {
                        CommandBarController.INSTANCE.handleCommandInput(textField1.getText(), editor);
                    }
                }
            }
        });
        textField1.setFocusTraversalKeysEnabled(false);
        textField1.putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true);
        textField1.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
            }

            @Override
            public void focusLost(FocusEvent e) {
                    dispose();
            }
        });

        label2.setCursor(Cursor.getDefaultCursor());
        label2.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                label2.setIcon(IconLoader.getIcon("/icons/closeIconHovered.svg"));
            }
        });
        addRecursiveMouseListener(label2, new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dispose();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                label2.setIcon(IconLoader.getIcon("/icons/closeIconPressed.svg"));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                label2.setIcon(IconLoader.getIcon("/icons/closeIconHovered.svg"));
            }
        }, () -> {
            removeActiveDecorations();
            return null;
        });
    }

    private void dispose() {
        if (disposed) return;
        disposed = true;
        editor.getScrollingModel().removeVisibleAreaListener(this);
        inlayMark.dispose(true, false);
    }

    private void removeActiveDecorations() {
        label2.setIcon(IconLoader.getIcon("/icons/closeIcon.svg"));
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner Evaluation license - unknown
        label1 = new JLabel();
        textField1 = new AutocompleteField("/", "Search or Type a Command (/)", Arrays.stream(CommandAction.values()).collect(Collectors.toList()), lookup, inlayMark.getLineNumber(), true, true);
        label2 = new JLabel();

        //======== this ========
        setPreferredSize(new Dimension(500, 40));
        setMinimumSize(new Dimension(500, 40));
        setBorder(new LineBorder(new Color(85, 85, 85)));
        setBackground(new Color(43, 43, 43));
        setLayout(new MigLayout(
            "hidemode 3",
            // columns
            "[fill]" +
            "[grow,fill]" +
            "[fill]",
            // rows
            "0[grow]0"));

        //---- label1 ----
        label1.setIcon(IconLoader.getIcon("/icons/command/logo.svg"));
        add(label1, "cell 0 0");

        //---- textField1 ----
        textField1.setBackground(new Color(37, 37, 37));
        textField1.setBorder(new CompoundBorder(
            new LineBorder(Color.darkGray, 1, true),
            new EmptyBorder(2, 6, 0, 0)));
        textField1.setFont(new Font("Roboto Light", Font.PLAIN, 14));
        textField1.setMinimumSize(new Dimension(0, 27));
        add(textField1, "cell 1 0");

        //---- label2 ----
        label2.setIcon(IconLoader.getIcon("/icons/closeIcon.svg"));
        add(label2, "cell 2 0");
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner Evaluation license - unknown
    private JLabel label1;
    private AutocompleteField textField1;
    private JLabel label2;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
