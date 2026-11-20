// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.io.OnlineResource;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.tools.ReflectionUtils;

/**
 * Unit tests of {@link AuthenticationPreferencesPanel}.
 */
@BasicPreferences
class AuthenticationPreferencesPanelTest {

    @AfterEach
    void tearDown() {
        ExpertToggleAction.getInstance().setExpert(false);
        NetworkManager.setOnline(OnlineResource.ALL);
        Config.getPref().put("osm-server.auth-method", null);
        Config.getPref().put("osm-server.url", null);
    }

    private static AuthenticationPreferencesPanel createPanel(String authMethod) {
        // the OAuth panel looks up the default parameters of the API, which must not make a network request
        NetworkManager.setOffline(OnlineResource.ALL);
        Config.getPref().put("osm-server.auth-method", authMethod);
        return new AuthenticationPreferencesPanel();
    }

    private static Object getField(AuthenticationPreferencesPanel panel, String name) throws ReflectiveOperationException {
        final Field field = AuthenticationPreferencesPanel.class.getDeclaredField(name);
        ReflectionUtils.setObjectsAccessible(field);
        return field.get(panel);
    }

    private static boolean isMethodChoiceVisible(AuthenticationPreferencesPanel panel) throws ReflectiveOperationException {
        return ((JPanel) getField(panel, "pnlAuthenticationMethod")).isVisible();
    }

    /**
     * Basic authentication is for experts: a normal user using OAuth 2.0 is not offered the choice.
     * @throws ReflectiveOperationException if a field cannot be accessed
     */
    @Test
    void testMethodChoiceHiddenFromNormalUsers() throws ReflectiveOperationException {
        ExpertToggleAction.getInstance().setExpert(false);
        assertFalse(isMethodChoiceVisible(createPanel("oauth20")));
    }

    /**
     * A normal user whose profile still uses basic authentication keeps seeing the setting, so that it can be changed.
     * @throws ReflectiveOperationException if a field cannot be accessed
     */
    @Test
    void testMethodChoiceShownWhileBasicAuthenticationIsUsed() throws ReflectiveOperationException {
        ExpertToggleAction.getInstance().setExpert(false);
        final AuthenticationPreferencesPanel panel = createPanel("basic");
        assertTrue(((JRadioButton) getField(panel, "rbBasicAuthentication")).isSelected());
        assertTrue(isMethodChoiceVisible(panel));
    }

    /**
     * Basic authentication is not offered for the servers which accept OAuth 2.0 only, even to experts, unless it
     * is in use already, so that it can be changed.
     * @throws ReflectiveOperationException if a field cannot be accessed
     */
    @Test
    void testNoBasicAuthenticationForOAuthOnlyServers() throws ReflectiveOperationException {
        ExpertToggleAction.getInstance().setExpert(true);
        for (String apiUrl : new String[] {Config.getUrls().getDefaultOsmApiUrl(), "https://opengeofiction.net/api",
                "https://opengeofiction.net/api/"}) {
            Config.getPref().put("osm-server.url", apiUrl);
            assertFalse(((JRadioButton) getField(createPanel("oauth20"), "rbBasicAuthentication")).isEnabled(), apiUrl);
            assertTrue(((JRadioButton) getField(createPanel("basic"), "rbBasicAuthentication")).isEnabled(),
                    () -> apiUrl + ": a user of basic authentication could not leave it");
        }
        Config.getPref().put("osm-server.url", "https://nonprod-mapops.example.org/api");
        assertTrue(((JRadioButton) getField(createPanel("oauth20"), "rbBasicAuthentication")).isEnabled());
    }

    /**
     * The choice follows expert mode, unless basic authentication is selected.
     * <p>
     * {@link ExpertToggleAction} only keeps weak references to its listeners, so the panel has to hold on to its
     * own; the garbage collection makes sure the test notices if it does not.
     * @throws ReflectiveOperationException if a field cannot be accessed
     */
    @Test
    void testMethodChoiceFollowsExpertMode() throws ReflectiveOperationException {
        ExpertToggleAction.getInstance().setExpert(true);
        final AuthenticationPreferencesPanel panel = createPanel("oauth20");
        assertTrue(isMethodChoiceVisible(panel));
        System.gc();

        ExpertToggleAction.getInstance().setExpert(false);
        assertFalse(isMethodChoiceVisible(panel));
        ExpertToggleAction.getInstance().setExpert(true);
        assertTrue(isMethodChoiceVisible(panel));

        // an expert selects basic authentication, then leaves expert mode: the setting stays in sight
        ((JRadioButton) getField(panel, "rbBasicAuthentication")).setSelected(true);
        ExpertToggleAction.getInstance().setExpert(false);
        assertTrue(isMethodChoiceVisible(panel));
    }
}
