/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.fontbox.ttf;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This 'name'-table is a required table in a TrueType font.
 *
 * @author Ben Litchfield
 */
public class NamingTable extends TTFTable
{
    /**
     * A tag that identifies this table type.
     */
    public static final String TAG = "name";

    private List<NameRecord> nameRecords;

    private String fontFamily = null;
    private String fontSubFamily = null;
    private String psName = null;
    private byte[] stringTable;

    NamingTable()
    {
        super();
    }

    /**
     * This will read the required data from the stream.
     *
     * @param ttf The font that is being read.
     * @param data The stream to read the data from.
     * @throws IOException If there is an error reading the data.
     */
    @Override
    void read(TrueTypeFont ttf, TTFDataStream data) throws IOException
    {
        int formatSelector = data.readUnsignedShort();
        int numberOfNameRecords = data.readUnsignedShort();
        int offsetToStartOfStringStorage = data.readUnsignedShort();
        nameRecords = new ArrayList<>(numberOfNameRecords);
        LoadOnlyHeaders onlyHeaders = ttf.getLoadOnlyHeaders();
        for (int i=0; i< numberOfNameRecords; i++)
        {
            NameRecord nr = new NameRecord();
            nr.initData(ttf, data);
            if (onlyHeaders == null || isUsefulForOnlyHeaders(nr))
            {
                nameRecords.add(nr);
            }
        }
        final long stringsStart = data.getCurrentPosition(); // == getOffset() + (2L*3)+numberOfNameRecords*2L*6
        stringTable = onlyHeaders == null
                ? data.read((int) (getOffset() + getLength() - stringsStart))
                : null;
        if (onlyHeaders != null)
        {
            // preload strings - profiler shows that it is faster than using 'stringTable' or 'data.readString' on demand
            for (NameRecord nr : nameRecords)
            {
                // don't try to read invalid offsets, see PDFBOX-2608
                if (nr.getStringOffset() > getLength())
                {
                    nr.setString(null);
                    continue;
                }

                data.seek(stringsStart + nr.getStringOffset());
                String string = data.readString(nr.getStringLength(), getCharset(nr));
                nr.setString(string);
            }
        }

        // sort to be able to binarySearch()
        Collections.sort(nameRecords, NamingTable::compareWithoutString);

        readInterestingStrings(onlyHeaders);
        initialized = true;
    }

    private Charset getCharset(NameRecord nr)
    {
        int platform = nr.getPlatformId();
        int encoding = nr.getPlatformEncodingId();
        Charset charset = StandardCharsets.ISO_8859_1;
        if (platform == NameRecord.PLATFORM_WINDOWS && (encoding == NameRecord.ENCODING_WINDOWS_SYMBOL || encoding == NameRecord.ENCODING_WINDOWS_UNICODE_BMP))
        {
            charset = StandardCharsets.UTF_16;
        }
        else if (platform == NameRecord.PLATFORM_UNICODE)
        {
            charset = StandardCharsets.UTF_16;
        }
        else if (platform == NameRecord.PLATFORM_ISO)
        {
            switch (encoding)
            {
                case 0:
                    charset = StandardCharsets.US_ASCII;
                    break;
                case 1:
                    //not sure is this is correct??
                    charset = StandardCharsets.UTF_16BE;
                    break;
                case 2:
                    charset = StandardCharsets.ISO_8859_1;
                    break;
                default:
                    break;
            }
        }
        return charset;
    }

    private void readInterestingStrings(LoadOnlyHeaders onlyHeaders)
    {
        // extract strings of interest
        fontFamily = getEnglishName(NameRecord.NAME_FONT_FAMILY_NAME);
        fontSubFamily = getEnglishName(NameRecord.NAME_FONT_SUB_FAMILY_NAME);

        // extract PostScript name, only these two formats are valid
        psName = getName(NameRecord.NAME_POSTSCRIPT_NAME,
                         NameRecord.PLATFORM_MACINTOSH,
                         NameRecord.ENCODING_MACINTOSH_ROMAN,
                         NameRecord.LANGUAGE_MACINTOSH_ENGLISH);
        if (psName == null)
        {
            psName = getName(NameRecord.NAME_POSTSCRIPT_NAME,
                             NameRecord.PLATFORM_WINDOWS,
                             NameRecord.ENCODING_WINDOWS_UNICODE_BMP,
                             NameRecord.LANGUAGE_WINDOWS_EN_US);
        }
        if (psName != null)
        {
            psName = psName.trim();
        }

        if (onlyHeaders != null)
        {
            onlyHeaders.setName(psName);
            onlyHeaders.setFontFamily(fontFamily, fontSubFamily);
        }
    }

    private static boolean isUsefulForOnlyHeaders(NameRecord nr)
    {
        int nameId = nr.getNameId();
        // see "psName =" and "getEnglishName()"
        if (nameId == NameRecord.NAME_POSTSCRIPT_NAME
                || nameId == NameRecord.NAME_FONT_FAMILY_NAME
                || nameId == NameRecord.NAME_FONT_SUB_FAMILY_NAME)
        {
            int languageId = nr.getLanguageId();
            return languageId == NameRecord.LANGUAGE_UNICODE
                    || languageId == NameRecord.LANGUAGE_WINDOWS_EN_US;
        }
        return false;
    }

    private static int compareWithoutString(NameRecord left, NameRecord right)
    {
        int result = Integer.compare(left.getNameId(), right.getNameId());
        if (result == 0)
        {
            result = Integer.compare(left.getPlatformId(), right.getPlatformId());
        }
        if (result == 0)
        {
            result = Integer.compare(left.getPlatformEncodingId(), right.getPlatformEncodingId());
        }
        if (result == 0)
        {
            result = Integer.compare(left.getLanguageId(), right.getLanguageId());
        }
        return result;
    }

    /**
     * Helper to get English names by best effort.
     */
    private String getEnglishName(int nameId)
    {
        // Unicode, Full, BMP, 1.1, 1.0
        for (int i = 4; i >= 0; i--)
        {
            String nameUni =
                    getName(nameId,
                            NameRecord.PLATFORM_UNICODE,
                            i,
                            NameRecord.LANGUAGE_UNICODE);
            if (nameUni != null)
            {
                return nameUni;
            }
        }

        // Windows, Unicode BMP, EN-US
        String nameWin =
                getName(nameId,
                        NameRecord.PLATFORM_WINDOWS,
                        NameRecord.ENCODING_WINDOWS_UNICODE_BMP,
                        NameRecord.LANGUAGE_WINDOWS_EN_US);
        if (nameWin != null)
        {
            return nameWin;
        }

        // Macintosh, Roman, English
        return getName(nameId,
                        NameRecord.PLATFORM_MACINTOSH,
                        NameRecord.ENCODING_MACINTOSH_ROMAN,
                        NameRecord.LANGUAGE_MACINTOSH_ENGLISH);
    }

    /**
     * Returns a name from the table, or null it it does not exist.
     *
     * @param nameId Name ID from NameRecord constants.
     * @param platformId Platform ID from NameRecord constants.
     * @param encodingId Platform Encoding ID from NameRecord constants.
     * @param languageId Language ID from NameRecord constants.
     * @return name, or null
     */
    public String getName(int nameId, int platformId, int encodingId, int languageId)
    {
        final NameRecord match = new NameRecord();
        match.setNameId(nameId);
        match.setPlatformId(platformId);
        match.setPlatformEncodingId(encodingId);
        match.setLanguageId(languageId);
        final int found = Collections.binarySearch(nameRecords, match, NamingTable::compareWithoutString);
        return found < 0 ? null : getString(nameRecords.get(found));
    }

    public byte[] getStringBytes(NameRecord nr)
    {
        final int start = nr.getStringOffset();
        final int end = start + nr.getStringLength();
        return start < stringTable.length
                ? Arrays.copyOfRange(stringTable, start, Math.min(end, stringTable.length))
                // don't try to read invalid offsets, see PDFBOX-2608
                : null;
    }

    public String getString(NameRecord nr)
    {
        // don't try to read invalid offsets, see PDFBOX-2608
        if (nr == null || nr.getStringOffset() >= getLength())
        {
            return null;
        }
        String preloaded = nr.getStringLazy();
        if (preloaded == null)
        {
            preloaded = new String(stringTable, nr.getStringOffset(), nr.getStringLength(), getCharset(nr));
            nr.setString(preloaded);
        }
        return preloaded;
    }

    /**
     * This will get the name records for this naming table.
     *
     * @return A list of NameRecord objects.
     */
    public List<NameRecord> getNameRecords()
    {
        return nameRecords;
    }

    /**
     * Returns the font family name, in English.
     *
     * @return the font family name, in English
     */
    public String getFontFamily()
    {
        return fontFamily;
    }

    /**
     * Returns the font sub family name, in English.
     *
     * @return the font sub family name, in English
     */
    public String getFontSubFamily()
    {
        return fontSubFamily;
    }

    /**
     * Returns the PostScript name.
     *
     * @return the PostScript name
     */
    public String getPostScriptName()
    {
        return psName;
    }
}
