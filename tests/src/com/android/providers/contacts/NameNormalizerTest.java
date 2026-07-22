/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.contacts;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import java.text.RuleBasedCollator;
import java.util.Locale;

/**
 * Unit tests for {@link NameNormalizer}.
 *
 * Run the test like this:
 * <code>
   adb shell am instrument -e class com.android.providers.contacts.NameNormalizerTest -w \
           com.android.providers.contacts.tests/android.test.InstrumentationTestRunner
 * </code>
 */
@SmallTest
public class NameNormalizerTest extends TestCase {

    private Locale mOriginalLocale;


    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mOriginalLocale = Locale.getDefault();

        // Run all test in en_US
        Locale.setDefault(Locale.US);
    }

    @Override
    protected void tearDown() throws Exception {
        Locale.setDefault(mOriginalLocale);
        super.tearDown();
    }

    public void testDifferent() {
        final String name1 = NameNormalizer.normalize("Helene");
        final String name2 = NameNormalizer.normalize("Francesca");
        assertFalse(name2.equals(name1));
    }

    public void testAccents() {
        final String name1 = NameNormalizer.normalize("Helene");
        final String name2 = NameNormalizer.normalize("H\u00e9l\u00e8ne");
        assertTrue(name2.equals(name1));
    }

    public void testMixedCase() {
        final String name1 = NameNormalizer.normalize("Helene");
        final String name2 = NameNormalizer.normalize("hEL\uFF25NE"); // FF25 = FULL WIDTH E
        assertTrue(name2.equals(name1));
    }

    public void testNonLetters() {
        // U+FF1E: 'FULLWIDTH GREATER-THAN SIGN'
        // U+FF03: 'FULLWIDTH NUMBER SIGN'
        final String name1 = NameNormalizer.normalize("h-e?l \uFF1ee+\uFF03n=e");
        final String name2 = NameNormalizer.normalize("helene");
        assertTrue(name2.equals(name1));
    }

    public void testArabicAlefVariants() {
        // Ahmad spelled with alef with hamza above vs. bare alef
        final String name1 = NameNormalizer.normalize("\u0623\u062D\u0645\u062F");
        final String name2 = NameNormalizer.normalize("\u0627\u062D\u0645\u062F");
        assertTrue(name2.equals(name1));

        // Ibrahim spelled with alef with hamza below vs. bare alef
        final String name3 = NameNormalizer.normalize("\u0625\u0628\u0631\u0627\u0647\u064A\u0645");
        final String name4 = NameNormalizer.normalize("\u0627\u0628\u0631\u0627\u0647\u064A\u0645");
        assertTrue(name4.equals(name3));

        // Amna spelled with alef with madda above vs. bare alef
        final String name5 = NameNormalizer.normalize("\u0622\u0645\u0646\u0647");
        final String name6 = NameNormalizer.normalize("\u0627\u0645\u0646\u0647");
        assertTrue(name6.equals(name5));
    }

    public void testArabicTehMarbuta() {
        // Hiba spelled with teh marbuta vs. heh
        final String name1 = NameNormalizer.normalize("\u0647\u0628\u0629");
        final String name2 = NameNormalizer.normalize("\u0647\u0628\u0647");
        assertTrue(name2.equals(name1));
    }

    public void testArabicAlefMaksura() {
        // Mustafa spelled with alef maksura vs. yeh
        final String name1 = NameNormalizer.normalize("\u0645\u0635\u0637\u0641\u0649");
        final String name2 = NameNormalizer.normalize("\u0645\u0635\u0637\u0641\u064A");
        assertTrue(name2.equals(name1));
    }

    public void testArabicTashkeelAndTatweel() {
        // Muhammad with tashkeel vs. without
        final String name1 = NameNormalizer.normalize("\u0645\u062D\u064E\u0645\u064E\u0651\u062F");
        final String name2 = NameNormalizer.normalize("\u0645\u062D\u0645\u062F");
        assertTrue(name2.equals(name1));

        // Muhammad with tatweel vs. without
        final String name3 = NameNormalizer.normalize("\u0645\u062D\u0640\u0640\u0645\u062F");
        assertTrue(name2.equals(name3));
    }

    public void testArabicDifferentNames() {
        // Hussam vs. Hassan should remain distinct
        final String name1 = NameNormalizer.normalize("\u062D\u0633\u0627\u0645");
        final String name2 = NameNormalizer.normalize("\u062D\u0633\u0627\u0646");
        assertFalse(name2.equals(name1));
    }

    public void testComplexityCase() {
        assertTrue(NameNormalizer.compareComplexity("Helene", "helene") > 0);
    }

    public void testComplexityAccent() {
        assertTrue(NameNormalizer.compareComplexity("H\u00e9lene", "Helene") > 0);
    }

    public void testComplexityLength() {
        assertTrue(NameNormalizer.compareComplexity("helene2009", "helene") > 0);
    }

    public void testGetCollators() {
        final RuleBasedCollator compressing1 = NameNormalizer.getCompressingCollator();
        final RuleBasedCollator complexity1 = NameNormalizer.getComplexityCollator();

        assertNotNull(compressing1);
        assertNotNull(complexity1);
        assertNotSame(compressing1, complexity1);

        // Get again.  Should be cached.
        final RuleBasedCollator compressing2 = NameNormalizer.getCompressingCollator();
        final RuleBasedCollator complexity2 = NameNormalizer.getComplexityCollator();

        assertSame(compressing1, compressing2);
        assertSame(complexity1, complexity2);

        // Change locale -- now new collators should be returned.
        Locale.setDefault(Locale.FRANCE);

        final RuleBasedCollator compressing3 = NameNormalizer.getCompressingCollator();
        final RuleBasedCollator complexity3 = NameNormalizer.getComplexityCollator();

        assertNotNull(compressing3);
        assertNotNull(complexity3);
        assertNotSame(compressing3, complexity3);

        assertNotSame(compressing1, compressing3);
        assertNotSame(complexity1, complexity3);
    }
}
