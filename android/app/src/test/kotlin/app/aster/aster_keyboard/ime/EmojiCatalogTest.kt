package app.aster.aster_keyboard.ime

import org.junit.Assert.*
import org.junit.Test

class EmojiCatalogTest {
    @Test fun gesturesOfferAllFiveSkinTonesWithoutTextPresentationSelectors() {
        assertEquals(listOf("🫶", "🫶🏻", "🫶🏼", "🫶🏽", "🫶🏾", "🫶🏿"), EmojiCatalog.variants("🫶"))
        assertEquals(listOf("☝️", "☝🏻", "☝🏼", "☝🏽", "☝🏾", "☝🏿"), EmojiCatalog.variants("☝️"))
    }

    @Test fun professionsAndDirectionalPeopleKeepTheirJoinedSequencesIntact() {
        assertEquals(listOf("👩‍⚕️", "👩🏻‍⚕️", "👩🏼‍⚕️", "👩🏽‍⚕️", "👩🏾‍⚕️", "👩🏿‍⚕️"),
            EmojiCatalog.variants("👩‍⚕️"))
        assertEquals("🧑🏿‍🦽‍➡️", EmojiCatalog.variants("🧑‍🦽‍➡️").last())
    }

    @Test fun unsupportedAndAlreadyModifiedEmojiNeverProduceInvalidVariants() {
        listOf("", "🐱", "🦾", "🇷🇴", "👨‍👩‍👧‍👦", "👍🏽").forEach {
            assertTrue("Unexpected skin tones for $it", EmojiCatalog.variants(it).isEmpty())
        }
    }
}
