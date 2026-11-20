// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.oauth;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openstreetmap.josm.data.oauth.IOAuthParameters;
import org.openstreetmap.josm.data.oauth.OAuth20Parameters;
import org.openstreetmap.josm.data.oauth.OAuthParameters;
import org.openstreetmap.josm.data.oauth.OAuthVersion;
import org.openstreetmap.josm.gui.HelpAwareOptionPane;
import org.openstreetmap.josm.gui.HelpAwareOptionPane.ButtonSpec;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.gui.widgets.SelectAllOnFocusGainedDecorator;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Panel allowing the user to setup advanced OAuth parameters:
 * <ul>
 * <li>Client ID</li>
 * <li>Client secret</li>
 * <li>Redirect URI</li>
 * <li>Access token URL</li>
 * <li>Authorize URL</li>
 * </ul>
 *
 * @see OAuthParameters
 * @since 2746
 */
public class AdvancedOAuthPropertiesPanel extends VerticallyScrollablePanel {
    /**
     * Property fired whenever the parameters this panel would return change: the client id was edited,
     * or "use default settings" was toggled. Listeners re-read {@link #getAdvancedParameters()}.
     * @since 19627
     */
    public static final String PARAMETERS_CHANGED_PROP = AdvancedOAuthPropertiesPanel.class.getName() + ".parametersChanged";

    private final JCheckBox cbUseDefaults = new JCheckBox(tr("Use default settings"));
    private final JosmTextField tfClientId = new JosmTextField();
    private final JosmTextField tfClientSecret = new JosmTextField();
    private final JosmTextField tfRedirectUri = new JosmTextField();
    private final JosmTextField tfAccessTokenURL = new JosmTextField();
    private final JosmTextField tfAuthoriseURL = new JosmTextField();
    private final OAuthVersion oauthVersion;
    private transient UseDefaultItemListener ilUseDefault;
    private String apiUrl;

    /**
     * Constructs a new {@code AdvancedOAuthPropertiesPanel}.
     * @param oauthVersion The OAuth version to make the panel for
     */
    public AdvancedOAuthPropertiesPanel(OAuthVersion oauthVersion) {
        this.oauthVersion = oauthVersion;
        build();
    }

    protected final void build() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        GridBagConstraints gc = new GridBagConstraints();

        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.insets = new Insets(0, 0, 3, 3);
        gc.gridwidth = 3;
        add(cbUseDefaults, gc);

        // -- client id
        gc.gridy = 1;
        gc.weightx = 0.0;
        gc.gridwidth = 1;
        add(new JLabel(tr("Client ID:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        add(tfClientId, gc);
        SelectAllOnFocusGainedDecorator.decorate(tfClientId);

        // -- client secret
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        add(new JLabel(tr("Client Secret:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        add(tfClientSecret, gc);
        SelectAllOnFocusGainedDecorator.decorate(tfClientSecret);

        // -- redirect URI
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        add(new JLabel(tr("Redirect URL:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        add(tfRedirectUri, gc);
        SelectAllOnFocusGainedDecorator.decorate(tfRedirectUri);

        // -- access token URL
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        add(new JLabel(tr("Access Token URL:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        add(tfAccessTokenURL, gc);
        SelectAllOnFocusGainedDecorator.decorate(tfAccessTokenURL);

        // -- authorise URL
        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0.0;
        add(new JLabel(tr("Authorize URL:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        add(tfAuthoriseURL, gc);
        SelectAllOnFocusGainedDecorator.decorate(tfAuthoriseURL);

        ilUseDefault = new UseDefaultItemListener();
        cbUseDefaults.addItemListener(ilUseDefault);
        cbUseDefaults.setSelected(true);
        cbUseDefaults.addItemListener(e -> fireParametersChanged());
        tfClientId.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fireParametersChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fireParametersChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fireParametersChanged();
            }
        });
    }

    private void fireParametersChanged() {
        firePropertyChange(PARAMETERS_CHANGED_PROP, null, null);
    }

    /**
     * Determines if the panel is set to use the default parameters for the API URL rather than
     * the values entered in its fields.
     * @return {@code true} if "use default settings" is selected
     * @since 19627
     */
    public boolean isUseDefaultSettings() {
        return cbUseDefaults.isSelected();
    }

    /**
     * Get the client id as entered in the panel, without falling back to the defaults.
     * @return the client id field's text
     * @since 19627
     */
    public String getEnteredClientId() {
        return tfClientId.getText();
    }

    protected boolean hasCustomSettings() {
        IOAuthParameters params = OAuthParameters.createDefault(apiUrl, this.oauthVersion);
        return !params.equals(getAdvancedParameters());
    }

    protected boolean confirmOverwriteCustomSettings() {
        ButtonSpec[] buttons = {
                new ButtonSpec(
                        tr("Continue"),
                        new ImageProvider("ok"),
                        tr("Click to reset the OAuth settings to default values"),
                        null /* no dedicated help topic */
                ),
                new ButtonSpec(
                        tr("Cancel"),
                        new ImageProvider("cancel"),
                        tr("Click to abort resetting to the OAuth default values"),
                        null /* no dedicated help topic */
                )
        };
        return 0 == HelpAwareOptionPane.showOptionDialog(
                this,
                tr(
                        "<html>JOSM is about to reset the OAuth settings to default values.<br>"
                        + "The current custom settings are not saved.</html>"
                ),
                tr("Overwrite custom OAuth settings?"),
                JOptionPane.WARNING_MESSAGE,
                null, /* no dedicated icon */
                buttons,
                buttons[0],
                HelpUtil.ht("/Dialog/OAuthAuthorisationWizard")
        );
    }

    protected void resetToDefaultSettings() {
        IOAuthParameters iParams = OAuthParameters.createDefault(apiUrl, this.oauthVersion);
        GuiHelper.runInEDTAndWaitWithException(() -> {
                    cbUseDefaults.setSelected(true);
                    switch (this.oauthVersion) {
                        case OAuth20:
                        case OAuth21:
                            OAuth20Parameters params20 = (OAuth20Parameters) iParams;
                            tfClientId.setText(params20.getClientId());
                            tfClientSecret.setText(params20.getClientSecret());
                            tfAccessTokenURL.setText(params20.getAccessTokenUrl());
                            tfAuthoriseURL.setText(params20.getAuthorizationUrl());
                            tfRedirectUri.setText(params20.getRedirectUri());
                            break;
                        default:
                            throw new UnsupportedOperationException("Unsupported OAuth version: " + this.oauthVersion);
                    }
                    setChildComponentsEnabled(false);
                });
    }

    protected void setChildComponentsEnabled(boolean enabled) {
        for (Component c: getComponents()) {
            if (c instanceof JosmTextField || c instanceof JLabel) {
                c.setEnabled(enabled);
            }
        }
    }

    /**
     * Replies the OAuth parameters currently edited in this properties panel.
     *
     * @return the OAuth parameters
     */
    public IOAuthParameters getAdvancedParameters() {
        if (cbUseDefaults.isSelected())
            return OAuthParameters.createDefault(apiUrl, this.oauthVersion);
        return new OAuth20Parameters(
                tfClientId.getText(),
                tfClientSecret.getText(),
                tfAccessTokenURL.getText(),
                tfAuthoriseURL.getText(),
                apiUrl,
                tfRedirectUri.getText()
                );
    }

    /**
     * Sets the advanced parameters to be displayed
     *
     * @param parameters the advanced parameters. Must not be null.
     * @throws IllegalArgumentException if parameters is null.
     */
    public void setAdvancedParameters(IOAuthParameters parameters) {
        CheckParameterUtil.ensureParameterNotNull(parameters, "parameters");
        if (parameters.equals(OAuthParameters.createDefault(apiUrl, parameters.getOAuthVersion()))) {
            cbUseDefaults.setSelected(true);
            setChildComponentsEnabled(false);
        } else {
            cbUseDefaults.setSelected(false);
            setChildComponentsEnabled(true);
            if (parameters instanceof OAuth20Parameters) {
                OAuth20Parameters parameters20 = (OAuth20Parameters) parameters;
                tfClientId.setText(parameters20.getClientId());
                tfClientSecret.setText(parameters20.getClientSecret());
                tfAccessTokenURL.setText(parameters20.getAccessTokenUrl());
                tfAuthoriseURL.setText(parameters20.getAuthorizationUrl());
                tfRedirectUri.setText(parameters20.getRedirectUri());
            }
        }
    }

    /**
     * Initializes the panel from the values in the preferences <code>preferences</code>.
     *
     * @param paramApiUrl the API URL. Must not be null.
     * @throws IllegalArgumentException if paramApiUrl is null
     */
    public void initialize(String paramApiUrl) {
        CheckParameterUtil.ensureParameterNotNull(paramApiUrl, "paramApiUrl");
        this.apiUrl = paramApiUrl;
        // "use default settings" is a setting of each server
        boolean useDefault = OAuthParameters.isUseDefault(paramApiUrl);
        ilUseDefault.setEnabled(false);
        cbUseDefaults.setSelected(useDefault);
        MainApplication.worker.execute(() -> {
            if (useDefault) {
                this.resetToDefaultSettings();
            } else {
                // createFromApiUrl may make a network request
                final IOAuthParameters parameters = OAuthParameters.createFromApiUrl(paramApiUrl, this.oauthVersion);
                GuiHelper.runInEDT(() -> setAdvancedParameters(parameters));
            }
            GuiHelper.runInEDT(() -> ilUseDefault.setEnabled(true));
        });
    }

    /**
     * Remembers the current values in the preferences <code>pref</code>: whether the server uses the default
     * settings, and the settings entered if not. Settings entered earlier are kept when the default settings are
     * used, so that they are there again when they are needed again.
     */
    public void rememberPreferences() {
        if (apiUrl == null) {
            return; // not initialized
        }
        OAuthParameters.setUseDefault(apiUrl, cbUseDefaults.isSelected());
        if (!cbUseDefaults.isSelected()) {
            getAdvancedParameters().rememberPreferences();
        }
    }

    class UseDefaultItemListener implements ItemListener {
        private boolean enabled;

        @Override
        public void itemStateChanged(ItemEvent e) {
            if (!enabled) return;
            switch (e.getStateChange()) {
            case ItemEvent.SELECTED:
                if (hasCustomSettings() && !confirmOverwriteCustomSettings()) {
                    cbUseDefaults.setSelected(false);
                    return;
                }
                MainApplication.worker.execute(AdvancedOAuthPropertiesPanel.this::resetToDefaultSettings);
                break;
            case ItemEvent.DESELECTED:
                setChildComponentsEnabled(true);
                break;
            default: // Do nothing
            }
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Sets the URL of the OSM API for which this panel is currently displaying OAuth properties.
     *
     * @param apiUrl the api URL
     * @since 5422
     */
    public void setApiUrl(String apiUrl) {
        if (this.apiUrl != null && apiUrl != null
                && !Objects.equals(OAuthParameters.getHost(this.apiUrl), OAuthParameters.getHost(apiUrl))) {
            // another server, with settings of its own
            initialize(apiUrl);
            return;
        }
        this.apiUrl = apiUrl;
        if (cbUseDefaults.isSelected()) {
            MainApplication.worker.execute(this::resetToDefaultSettings);
        }
    }
}
