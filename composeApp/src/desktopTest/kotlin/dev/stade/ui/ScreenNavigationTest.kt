package dev.stade.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenNavigationTest {

    private fun goesForward(from: Screen, to: Screen) = screenDepth(to) >= screenDepth(from)

    @Test
    fun settingsSubScreensAnimateBackToSettings() {
        for (child in listOf(Screen.About, Screen.Security, Screen.Transports)) {
            assertTrue(goesForward(Screen.Settings, child), "Settings -> $child should go forward")
            assertTrue(!goesForward(child, Screen.Settings), "$child -> Settings should go back")
        }
    }

    @Test
    fun chatOpensForwardAndClosesBackward() {
        val chat = Screen.Chat("abc")
        assertTrue(goesForward(Screen.Contacts, chat), "opening a chat should go forward")
        assertTrue(!goesForward(chat, Screen.Contacts), "leaving a chat should go back")
    }

    @Test
    fun groupMembersSitsDeeperThanItsGroupChat() {
        val group = Screen.GroupChat("g1")
        val members = Screen.GroupMembers("g1")
        assertTrue(goesForward(group, members))
        assertTrue(!goesForward(members, group))
    }

    @Test
    fun manageStadiumSitsDeeperThanItsStadium() {
        val stadium = Screen.Stadium("s1")
        val manage = Screen.ManageStadium("s1")
        assertTrue(goesForward(stadium, manage))
        assertTrue(!goesForward(manage, stadium))
    }

    @Test
    fun verifySitsDeeperThanTheChatItOpensFrom() {
        val chat = Screen.Chat("abc")
        val verify = Screen.Verify("abc", fromScreen = chat)
        assertTrue(goesForward(chat, verify))
        assertTrue(!goesForward(verify, chat))
    }

    @Test
    fun contactsIsShallowerThanEveryOtherMainScreen() {
        val mains = listOf(
            Screen.Settings, Screen.Stadey, Screen.AddContact, Screen.Radar,
            Screen.CreateGroup, Screen.CreateStadium, Screen.JoinStadium,
            Screen.Chat("a"), Screen.GroupChat("g"), Screen.Stadium("s")
        )
        for (s in mains) {
            assertTrue(screenDepth(s) > screenDepth(Screen.Contacts), "$s should be deeper than Contacts")
        }
    }

    @Test
    fun onboardingIsTheShallowestScreen() {
        assertEquals(0, screenDepth(Screen.Onboarding))
        assertTrue(screenDepth(Screen.Contacts) > screenDepth(Screen.Onboarding))
    }

    @Test
    fun highlightingAMessageDoesNotChangeTheScreenIdentity() {
        assertEquals(screenKey(Screen.Chat("a")), screenKey(Screen.Chat("a", highlightMessageId = "m1")))
        assertEquals(screenKey(Screen.GroupChat("g")), screenKey(Screen.GroupChat("g", highlightMessageId = "m1")))
        assertEquals(screenKey(Screen.Stadium("s")), screenKey(Screen.Stadium("s", highlightMessageId = "m1")))
    }

    @Test
    fun differentConversationsHaveDifferentKeys() {
        assertTrue(screenKey(Screen.Chat("a")) != screenKey(Screen.Chat("b")))
        assertTrue(screenKey(Screen.Chat("a")) != screenKey(Screen.GroupChat("a")))
        assertTrue(screenKey(Screen.Stadium("a")) != screenKey(Screen.ManageStadium("a")))
    }
}
