// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.server;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.Field;
import java.net.Authenticator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openstreetmap.josm.data.oauth.OAuth20Exception;
import org.openstreetmap.josm.data.oauth.OAuth20Parameters;
import org.openstreetmap.josm.data.oauth.OAuth20Token;
import org.openstreetmap.josm.data.oauth.OAuthAccessTokenHolder;
import org.openstreetmap.josm.data.oauth.OAuthParameters;
import org.openstreetmap.josm.data.oauth.OAuthVersion;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.oauth.AdvancedOAuthPropertiesPanel;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.io.OnlineResource;
import org.openstreetmap.josm.io.OsmApi;
import org.openstreetmap.josm.io.auth.CredentialsAgentException;
import org.openstreetmap.josm.io.auth.CredentialsManager;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.tools.ReflectionUtils;

/**
 * Test class for {@link OAuthAuthenticationPreferencesPanel}
 */
@BasicPreferences
class OAuthAuthenticationPreferencesPanelTest {
    @AfterEach
    void tearDown() {
        OAuthAccessTokenHolder.getInstance().clear();
        for (Authenticator.RequestorType type : Authenticator.RequestorType.values()) {
            CredentialsManager.getInstance().purgeCredentialsCache(type);
        }
        List<Exception> exceptionList = new ArrayList<>();
        try {
            CredentialsManager.getInstance().storeOAuthAccessToken(OsmApi.getOsmApi().getServerUrl(), null);
        } catch (CredentialsAgentException exception) {
            exceptionList.add(exception);
        }
        try {
            CredentialsManager.getInstance().storeOAuthAccessToken(OsmApi.getOsmApi().getHost(), null);
        } catch (CredentialsAgentException exception) {
            exceptionList.add(exception);
        }

        assertAll(exceptionList.stream().map(e -> () -> fail(e)));
        // removing a token keeps the OAuth settings of the server
        Config.getPref().removeSensitive("oauth.access-token.parameters.OAuth20." + OsmApi.getOsmApi().getHost());
        Config.getPref().put("oauth.settings.use-default." + OsmApi.getOsmApi().getHost(), null);
        NetworkManager.setOnline(OnlineResource.ALL);
    }

    @ParameterizedTest
    @EnumSource(value = OAuthVersion.class, names = "OAuth20")
    void testRemoveToken(OAuthVersion oAuthVersion) throws ReflectiveOperationException, CredentialsAgentException, OAuth20Exception {
        final OAuthAuthenticationPreferencesPanel panel = new OAuthAuthenticationPreferencesPanel(oAuthVersion);
        final Field pnlNotYetAuthorised = OAuthAuthenticationPreferencesPanel.class.getDeclaredField("pnlNotYetAuthorised");
        final Field pnlAlreadyAuthorised = OAuthAuthenticationPreferencesPanel.class.getDeclaredField("pnlAlreadyAuthorised");
        final JPanel holder = (JPanel) panel.getComponent(0);
        ReflectionUtils.setObjectsAccessible(pnlNotYetAuthorised, pnlAlreadyAuthorised);
        panel.initFromPreferences();
        assertNull(getAuthorization(oAuthVersion));
        assertSame(pnlNotYetAuthorised.get(panel), holder.getComponent(0), "No authentication should be set yet");
        addAuthorization(oAuthVersion);
        assertNotNull(getAuthorization(oAuthVersion));
        panel.initFromPreferences();
        assertSame(pnlAlreadyAuthorised.get(panel), holder.getComponent(0), "Authentication should now be set");
        assertNotNull(getAuthorization(oAuthVersion));
        final JButton action = (JButton) getAlreadyAuthorisedButtons(panel).getComponent(2);
        assertEquals(tr("Remove token"), action.getText(), "The selected button should be for removing the token");
        action.getAction().actionPerformed(null);
        panel.saveToPreferences(); // Save to preferences should make the removal permanent
        // Ensure that the token holder has been reset
        OAuthAccessTokenHolder.getInstance().clear();
        OAuthAccessTokenHolder.getInstance().init(CredentialsManager.getInstance());
        assertNull(getAuthorization(oAuthVersion), "No authentication data should be stored");
        panel.initFromPreferences();
        assertSame(pnlNotYetAuthorised.get(panel), holder.getComponent(0), "No authentication should be set");
    }

    /**
     * The "New Access Token" button follows the client id entered in the advanced OAuth parameters, like the
     * "Authorize now" button does. Non-regression test: the buttons of the panel shown when a token exists were
     * looked up by component index, which pointed at the filler panel instead, so this button was never updated.
     * @throws ReflectiveOperationException if a field cannot be accessed
     * @throws CredentialsAgentException if the token cannot be stored
     * @throws OAuth20Exception if the token cannot be built
     * @throws ExecutionException if waiting for the worker fails
     * @throws InterruptedException if waiting for the worker is interrupted
     */
    @Test
    void testRenewButtonFollowsTheClientId() throws ReflectiveOperationException, CredentialsAgentException, OAuth20Exception,
            ExecutionException, InterruptedException {
        // the default parameters of the API would otherwise be looked up with an RFC 8414 request
        NetworkManager.setOffline(OnlineResource.ALL);
        try {
            final OAuthAuthenticationPreferencesPanel panel = new OAuthAuthenticationPreferencesPanel(OAuthVersion.OAuth20);
            addAuthorization(OAuthVersion.OAuth20);
            panel.initFromPreferences();
            waitForWorkerAndEdt();

            final Field pnlAdvancedProperties = OAuthAuthenticationPreferencesPanel.class.getDeclaredField("pnlAdvancedProperties");
            final Field cbUseDefaults = AdvancedOAuthPropertiesPanel.class.getDeclaredField("cbUseDefaults");
            final Field tfClientId = AdvancedOAuthPropertiesPanel.class.getDeclaredField("tfClientId");
            ReflectionUtils.setObjectsAccessible(pnlAdvancedProperties, cbUseDefaults, tfClientId);
            final AdvancedOAuthPropertiesPanel advanced = (AdvancedOAuthPropertiesPanel) pnlAdvancedProperties.get(panel);
            ((JCheckBox) cbUseDefaults.get(advanced)).setSelected(false);
            final JTextField clientId = (JTextField) tfClientId.get(advanced);
            final JButton renew = (JButton) getAlreadyAuthorisedButtons(panel).getComponent(0);
            assertEquals(tr("New Access Token"), renew.getText());

            clientId.setText("");
            assertFalse(renew.isEnabled(), "without a client id there is nothing to authorize with");
            clientId.setText("a-client-id");
            assertTrue(renew.isEnabled());
        } finally {
            NetworkManager.setOnline(OnlineResource.ALL);
            Config.getPref().put("oauth.settings.use-default", null);
        }
    }

    /**
     * Removing the token keeps the OAuth settings entered for the server, so that a new token can be obtained
     * without entering them again.
     * @throws ReflectiveOperationException if a field cannot be accessed
     * @throws CredentialsAgentException if the token cannot be stored
     * @throws OAuth20Exception if the token cannot be built
     * @throws ExecutionException if waiting for the worker fails
     * @throws InterruptedException if waiting for the worker is interrupted
     */
    @Test
    void testRemoveTokenKeepsTheEnteredSettings() throws ReflectiveOperationException, CredentialsAgentException, OAuth20Exception,
            ExecutionException, InterruptedException {
        // the default parameters of the API would otherwise be looked up with an RFC 8414 request
        NetworkManager.setOffline(OnlineResource.ALL);
        // the user entered the settings, and obtained a token with them
        OAuthParameters.setUseDefault(OsmApi.getOsmApi().getServerUrl(), false);
        addAuthorization(OAuthVersion.OAuth20);
        final OAuthAuthenticationPreferencesPanel panel = new OAuthAuthenticationPreferencesPanel(OAuthVersion.OAuth20);
        panel.initFromPreferences();
        waitForWorkerAndEdt();
        ((JButton) getAlreadyAuthorisedButtons(panel).getComponent(2)).getAction().actionPerformed(null);
        panel.saveToPreferences();
        assertNull(getAuthorization(OAuthVersion.OAuth20));

        final OAuthAuthenticationPreferencesPanel reopened = new OAuthAuthenticationPreferencesPanel(OAuthVersion.OAuth20);
        reopened.initFromPreferences();
        waitForWorkerAndEdt();
        final AdvancedOAuthPropertiesPanel advanced = getAdvancedProperties(reopened);
        assertFalse(advanced.isUseDefaultSettings());
        assertEquals("fake_id", advanced.getEnteredClientId());
    }

    /**
     * The "Authorize now" button is enabled according to the latest change of the advanced OAuth parameters.
     * <p>
     * Non-regression test: with "use default settings" ticked, the client id is looked up on the worker thread, and
     * the result of that lookup was applied even when the parameters had changed again in the meantime, for example
     * because the box was unticked and no client id entered.
     * @throws ReflectiveOperationException if a field cannot be accessed
     * @throws ExecutionException if waiting for the worker fails
     * @throws InterruptedException if waiting for the worker is interrupted
     */
    @Test
    void testAuthorizeButtonFollowsTheLatestChange() throws ReflectiveOperationException, ExecutionException, InterruptedException {
        NetworkManager.setOffline(OnlineResource.ALL);
        final OAuthAuthenticationPreferencesPanel panel = new OAuthAuthenticationPreferencesPanel(OAuthVersion.OAuth20);
        panel.initFromPreferences();
        waitForWorkerAndEdt();
        final AdvancedOAuthPropertiesPanel advanced = getAdvancedProperties(panel);
        assertTrue(advanced.isUseDefaultSettings());
        final Field pnlNotYetAuthorised = OAuthAuthenticationPreferencesPanel.class.getDeclaredField("pnlNotYetAuthorised");
        ReflectionUtils.setObjectsAccessible(pnlNotYetAuthorised);
        final JButton authorize = (JButton) ((JPanel) pnlNotYetAuthorised.get(panel)).getComponent(1);
        assertTrue(authorize.isEnabled(), "JOSM has a client id for the default API");

        // hold the worker, so that the lookup made with the default settings finishes after the next change
        final CountDownLatch latch = new CountDownLatch(1);
        MainApplication.worker.execute(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            ((JTextField) getAdvancedField(advanced, "tfClientId")).setText("");
            ((JCheckBox) getAdvancedField(advanced, "cbUseDefaults")).setSelected(false);
            assertFalse(authorize.isEnabled(), "without a client id there is nothing to authorize with");
        } finally {
            latch.countDown();
        }
        waitForWorkerAndEdt();
        assertFalse(authorize.isEnabled(), "the lookup made with the default settings overrode a later change");
    }

    private static AdvancedOAuthPropertiesPanel getAdvancedProperties(OAuthAuthenticationPreferencesPanel panel)
            throws ReflectiveOperationException {
        final Field pnlAdvancedProperties = OAuthAuthenticationPreferencesPanel.class.getDeclaredField("pnlAdvancedProperties");
        ReflectionUtils.setObjectsAccessible(pnlAdvancedProperties);
        return (AdvancedOAuthPropertiesPanel) pnlAdvancedProperties.get(panel);
    }

    private static Object getAdvancedField(AdvancedOAuthPropertiesPanel advanced, String name) throws ReflectiveOperationException {
        final Field field = AdvancedOAuthPropertiesPanel.class.getDeclaredField(name);
        ReflectionUtils.setObjectsAccessible(field);
        return field.get(advanced);
    }

    /**
     * Waits until the panel has settled. The advanced parameters fill themselves in from the worker, then the EDT,
     * and setting them re-evaluates the "Authorize now" buttons, which queues more work on the worker that again
     * finishes on the EDT; so this goes round twice.
     * @throws ExecutionException if waiting for the worker fails
     * @throws InterruptedException if waiting for the worker is interrupted
     */
    private static void waitForWorkerAndEdt() throws ExecutionException, InterruptedException {
        for (int i = 0; i < 2; i++) {
            MainApplication.worker.submit(() -> { }).get();
            GuiHelper.runInEDTAndWait(() -> { });
        }
    }

    private static JPanel getAlreadyAuthorisedButtons(OAuthAuthenticationPreferencesPanel panel) throws ReflectiveOperationException {
        final Field pnlAlreadyAuthorised = OAuthAuthenticationPreferencesPanel.class.getDeclaredField("pnlAlreadyAuthorised");
        ReflectionUtils.setObjectsAccessible(pnlAlreadyAuthorised);
        final Object alreadyAuthorised = pnlAlreadyAuthorised.get(panel);
        final Field pnlButtons = alreadyAuthorised.getClass().getDeclaredField("pnlButtons");
        ReflectionUtils.setObjectsAccessible(pnlButtons);
        return (JPanel) pnlButtons.get(alreadyAuthorised);
    }

    /**
     * Add authorization preferences for the specified oauth version
     * @param oAuthVersion The oauth version to use
     * @throws CredentialsAgentException If something goes wrong
     * @throws OAuth20Exception If something goes wrong
     */
    private static void addAuthorization(OAuthVersion oAuthVersion) throws CredentialsAgentException, OAuth20Exception {
        switch (oAuthVersion) {
            case OAuth20:
            case OAuth21:
                CredentialsManager.getInstance().storeOAuthAccessToken(OsmApi.getOsmApi().getHost(),
                        new OAuth20Token(new OAuth20Parameters("fake_id", "fake_secret", "https://fake.url/token",
                                "https://fake.url/authorize", OsmApi.getOsmApi().getBaseUrl(), "http://127.0.0.1:8111/oauth_authorization"),
                                "{\"access_token\": \"fake_token\", \"token_type\": \"bearer\"}"));
        }
        assertNotNull(getAuthorization(oAuthVersion));
    }

    /**
     * Get the authorization token
     * @param oAuthVersion The oauth version
     * @return The token
     */
    private static Object getAuthorization(OAuthVersion oAuthVersion) {
        OAuthAccessTokenHolder.getInstance().clear();
        OAuthAccessTokenHolder.getInstance().setAccessToken(OsmApi.getOsmApi().getServerUrl(), null);
        OAuthAccessTokenHolder.getInstance().init(CredentialsManager.getInstance());
        // Ensure that we are not saving authorization data
        return OAuthAccessTokenHolder.getInstance().getAccessToken(OsmApi.getOsmApi().getServerUrl(), oAuthVersion);
    }
}
