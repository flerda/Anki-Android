/***************************************************************************************
 *                                                                                      *
 * Copyright (c) 2015 Frank Oltmanns <frank.oltmanns@gmail.com>                         *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.tests;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.util.Log;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.provider.FlashCardsContract;
import com.ichi2.libanki.Collection;

import java.util.Arrays;
import java.util.List;

/** Test cases for {@link com.ichi2.anki.provider.CardContentProvider}.
 *
 * These tests should cover all supported operations for each URI.
 */
public class ContentProviderTest extends AndroidTestCase {

    private static final String TEST_FIELD_VALUE = "test field value";
    private static String TEST_TAG = "aldskfhewjklhfczmxkjshf";
    private static String TEST_DECK = "glekrjterglknsdfflkgj";
    private int createdNotes;

    /** Initially create one note for each model.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Log.i(AnkiDroidApp.TAG, "setUp()");
        final ContentResolver cr = getContext().getContentResolver();
        //Query all available models
        final Cursor cursor = cr.query(FlashCardsContract.Model.CONTENT_URI, null, null, null, null);
        assertEquals(true, cursor.moveToFirst());
        int runs = 0;
        do {
            Long mId = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Model._ID));
            ContentValues values = new ContentValues();
            values.put(FlashCardsContract.Note.MID, mId);
            Uri newNoteUri = cr.insert(FlashCardsContract.Note.CONTENT_URI, values);

            //Now set a special tag, so that the note can easily be deleted after test
            Uri newNoteDataUri = Uri.withAppendedPath(newNoteUri,"data");
            values.clear();
            values.put(FlashCardsContract.DataColumns.MIMETYPE, FlashCardsContract.Data.Tags.CONTENT_ITEM_TYPE);
            values.put(FlashCardsContract.Data.Tags.TAG_CONTENT, TEST_TAG);
            assertEquals("Tag set", 1, cr.update(newNoteDataUri, values, null, null));
            runs++;
        } while (cursor.moveToNext());
        createdNotes = runs;
        assertNotSame("Check that at least one model exists, i.e. one note was created", runs, 0);
    }

    /** Remove the notes created in setUp().
     *
     * Using direct access to the collection, because there is no plan to include a delete
     * interface in the content provider.
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        Log.i(AnkiDroidApp.TAG, "tearDown()");
        Collection col;
        //Hack to prevent crashes on some (pre-Kitkat?) devices
        while (AnkiDroidApp.getInstance() == null || AnkiDroidApp.getHooks() == null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        if(!AnkiDroidApp.colIsOpen()){
            String colPath = AnkiDroidApp.getCollectionPath();
            col = AnkiDroidApp.openCollection(colPath);
        }
        else{
            col = AnkiDroidApp.getCol();
        }
        int deletedNotes;
        List<Long> noteIds = col.findNotes("tag:"+TEST_TAG);
        if((noteIds != null) && (noteIds.size()>0)) {
            long[] delNotes = new long[noteIds.size()];
            for(int i = 0; i < noteIds.size(); i++){
                delNotes[i] = noteIds.get(i);
            }
            col.remNotes(delNotes);
            deletedNotes = noteIds.size();
        }
        else{
            deletedNotes = 0;
        }
        assertEquals("Check that all created notes have been deleted", createdNotes, deletedNotes);
    }

    public void testQueryNoteIds(){
        final ContentResolver cr = getContext().getContentResolver();
        //Query all available notes
        final Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:"+TEST_TAG, null, null);
        assertNotNull(cursor);
        assertEquals("Move to beginning of cursor", true, cursor.moveToFirst());
        do {
            //Now iterate over all cursors
            for(int i = 0; i < FlashCardsContract.Note.DEFAULT_PROJECTION.length; i++) {
                String[] projection = removeFromProjection(FlashCardsContract.Note.DEFAULT_PROJECTION, i);
                String noteId = cursor.getString(cursor.getColumnIndex(FlashCardsContract.Note._ID));
                Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, noteId);
                final Cursor cur = cr.query(noteUri, projection, null, null, null);
                assertNotNull("Check that there is a valid cursor for detail data", cur);
                assertEquals("Check that there is exactly one result", 1, cur.getCount());
                assertEquals("Move to beginning of cursor after querying for detail data", true, cur.moveToFirst());
                //Check columns
                assertEquals("Check column count", projection.length, cur.getColumnCount());
                for(int j=0; j<projection.length; j++){
                    assertEquals("Check column name "+j, projection[j], cur.getColumnName(j));
                }
            }
        } while (cursor.moveToNext());
    }

    public void testQueryNotesProjection(){
        final ContentResolver cr = getContext().getContentResolver();
        //Query all available notes
        for(int i = 0; i < FlashCardsContract.Note.DEFAULT_PROJECTION.length; i++) {
            String[] projection = removeFromProjection(FlashCardsContract.Note.DEFAULT_PROJECTION, i);
            final Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI, projection, "tag:" + TEST_TAG, null, null);
            assertNotNull("Check that there is a valid cursor", cursor);
            assertEquals("Check number of results", createdNotes, cursor.getCount());
            assertEquals("Move to beginning of cursor", true, cursor.moveToFirst());
            //Check columns
            assertEquals("Check column count", projection.length, cursor.getColumnCount());
            for (int j = 0; j < projection.length; j++) {
                assertEquals("Check column name " + j, projection[j], cursor.getColumnName(j));
            }
        }
    }

    private String[] removeFromProjection(String[] inputProjection, int idx) {
        String[] outputProjection = new String[inputProjection.length-1];
        for(int i = 0; i<idx; i++){
            outputProjection[i]=inputProjection[i];
        }
        for(int i = idx+1; i<inputProjection.length; i++){
            outputProjection[i-1] = inputProjection[i];
        }
        return outputProjection;
    }

    public void testQueryNoteData(){
        final ContentResolver cr = getContext().getContentResolver();
        //Query all available notes
        final Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:"+TEST_TAG, null, null);
        assertNotNull(cursor);
        assertEquals("Move to beginning of cursor", true, cursor.moveToFirst());
        do {
            //Now iterate over all cursors
            Uri dataUri = Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, cursor.getString(cursor.getColumnIndex(FlashCardsContract.Note._ID))), "data");
            final Cursor cur = cr.query(dataUri, null, null, null, null);
            assertNotNull("Check that there is a valid cursor for detail data", cur);
            assertEquals("Move to beginning of cursor after querying for detail data", true, cur.moveToFirst());
            do {
                if (cur.getString(cur.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)).equals(FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE)) {
                    assertEquals("Check field content", "temp", cur.getString(cur.getColumnIndex(FlashCardsContract.Data.Field.FIELD_CONTENT)));
                } else if (cur.getString(cur.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)).equals(FlashCardsContract.Data.Tags.CONTENT_ITEM_TYPE)) {
                        assertEquals("Unknown tag", TEST_TAG, cur.getString(cur.getColumnIndex(FlashCardsContract.Data.Tags.TAG_CONTENT)));
                } else {
                    assertTrue("Unknown MIME type " + cur.getString(cursor.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)), false);
                }
            } while (cur.moveToNext());
        } while (cursor.moveToNext());
    }

    public void testUpdateNoteField(){
        final ContentResolver cr = getContext().getContentResolver();
        //Query all available notes
        final Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:"+TEST_TAG, null, null);
        assertNotNull(cursor);
        assertEquals("Move to beginning of cursor", true, cursor.moveToFirst());
        do {
            //Now iterate over all notes
            Uri dataUri = Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, cursor.getString(cursor.getColumnIndex(FlashCardsContract.Note._ID))), "data");
            Cursor cur = cr.query(dataUri, null, null, null, null);
            assertNotNull("Check that there is a valid cursor for detail data", cur);
            assertEquals("Move to beginning of cursor after querying for detail data", true, cur.moveToFirst());
            do {
                if (cur.getString(cur.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)).equals(FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE)) {
                    //Update field
                    ContentValues values = new ContentValues();
                    values.put(FlashCardsContract.DataColumns.MIMETYPE, FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE);
                    values.put(FlashCardsContract.Data.Field.FIELD_NAME, cur.getString(cur.getColumnIndex(FlashCardsContract.Data.Field.FIELD_NAME)));
                    values.put(FlashCardsContract.Data.Field.FIELD_CONTENT, TEST_FIELD_VALUE);
                    assertEquals("Tag set", 1, cr.update(dataUri, values, null, null));
                } else {
                    //ignore other data
                }
            } while (cur.moveToNext());

            //After update query again
            cur = cr.query(dataUri, null, null, null, null);
            assertNotNull("Check that there is a valid cursor for detail data after update", cur);
            assertEquals("Move to beginning of cursor after querying for detail data after update", true, cur.moveToFirst());
            do {
                if (cur.getString(cur.getColumnIndex(FlashCardsContract.DataColumns.MIMETYPE)).equals(FlashCardsContract.Data.Field.CONTENT_ITEM_TYPE)) {
                    assertEquals("Check field content", TEST_FIELD_VALUE, cur.getString(cur.getColumnIndex(FlashCardsContract.Data.Field.FIELD_CONTENT)));
                } else {
                    //ignore other data
                }
            } while (cur.moveToNext());
        } while (cursor.moveToNext());
    }

    public void testQueryAllModels(){
        final ContentResolver cr = getContext().getContentResolver();
        //Query all available models
        final Cursor cursor = cr.query(FlashCardsContract.Model.CONTENT_URI, null, null, null, null);
        assertNotNull(cursor);
        assertEquals("Move to beginning of cursor", true, cursor.moveToFirst());
        do{
            long modelId = cursor.getLong(cursor.getColumnIndex(FlashCardsContract.Model._ID));
            Uri modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
            final Cursor cur = cr.query(modelUri, null, null, null, null);
            assertNotNull(cur);
            assertEquals("Check that there is exactly one result", 1, cur.getCount());
            assertEquals("Move to beginning of cursor", true, cur.moveToFirst());
            String nameFromModels = cursor.getString(cursor.getColumnIndex(FlashCardsContract.Model.NAME));
            String nameFromModel = cur.getString(cursor.getColumnIndex(FlashCardsContract.Model.NAME));
            assertEquals("Check that model names are the same", nameFromModel, nameFromModels);
            String jsonFromModels = cursor.getString(cursor.getColumnIndex(FlashCardsContract.Model.JSONOBJECT));
            String jsonFromModel = cur.getString(cursor.getColumnIndex(FlashCardsContract.Model.JSONOBJECT));
            assertEquals("Check that jsonobjects are the same", jsonFromModel, jsonFromModels);
        } while (cursor.moveToNext());
    }

    public void testMoveToOtherDeck(){
        final ContentResolver cr = getContext().getContentResolver();
        //Query all available notes
        final Cursor cursor = cr.query(FlashCardsContract.Note.CONTENT_URI, null, "tag:"+TEST_TAG, null, null);
        assertNotNull(cursor);
        assertEquals("Move to beginning of cursor", true, cursor.moveToFirst());
        do {
            //Now iterate over all cursors
            Uri cardsUri = Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, cursor.getString(cursor.getColumnIndex(FlashCardsContract.Note._ID))), "cards");
            final Cursor cur = cr.query(cardsUri, null, null, null, null);
            assertNotNull("Check that there is a valid cursor after query for cards", cur);
            assertEquals("Move to beginning of cursor after query for cards", true, cur.moveToFirst());
            do {
                String deckName = cur.getString(cur.getColumnIndex(FlashCardsContract.Card.DECK_NAME));
                assertEquals("Make sure that card is in default deck", "Default", deckName);
                //Move to test deck
                ContentValues values = new ContentValues();
                values.put(FlashCardsContract.Card.DECK_NAME, TEST_DECK);
                Uri cardUri = Uri.withAppendedPath(cardsUri, cur.getString(cur.getColumnIndex(FlashCardsContract.Card.CARD_ORD)));
                cr.update(cardUri, values, null, null);
                Cursor movedCardCur = cr.query(cardUri, null, null, null, null);
                assertNotNull("Check that there is a valid cursor after moving card", movedCardCur);
                assertEquals("Move to beginning of cursor after moving card", true, movedCardCur.moveToFirst());
                deckName = movedCardCur.getString(movedCardCur.getColumnIndex(FlashCardsContract.Card.DECK_NAME));
                assertEquals("Make sure that card is in test deck", TEST_DECK, deckName);
            } while (cur.moveToNext());
        } while (cursor.moveToNext());
    }

    public void testUnsupportedOperations() {
        final ContentResolver cr = getContext().getContentResolver();
        ContentValues dummyValues = new ContentValues();
        Uri[] updateUris = {
                FlashCardsContract.Note.CONTENT_URI,
                Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"),
                Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"), "cards"),
                Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, "1234")
        };
        for (Uri uri : updateUris) {
            try {
                cr.update(uri, dummyValues, null, null);
            } catch (Exception e) {
                assertTrue("Check that exception was thrown on update for uri " + uri, true);
                continue;
            }
            assertFalse("Update on " + uri + " was supposed to not working", true);
        }
        Uri[] deleteUris = {
                FlashCardsContract.Note.CONTENT_URI,
                Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"),
                Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"), "data"),
                Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"), "cards"),
                Uri.withAppendedPath(Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"), "cards"), "2345"),
                FlashCardsContract.Model.CONTENT_URI,
                Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, "1234")
        };
        for (Uri uri : deleteUris) {
            try {
                cr.delete(uri, null, null);
            } catch (Exception e) {
                assertTrue("Check that exception was thrown on delete for uri " + uri, true);
                continue;
            }
            assertFalse("Delete on " + uri + " was supposed to not working", true);
        }
        Uri[] insertUris = {
                Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"),
                Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"), "data"),
                Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"), "cards"),
                Uri.withAppendedPath(Uri.withAppendedPath(Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, "1234"), "cards"), "2345"),
                FlashCardsContract.Model.CONTENT_URI,
                Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, "1234")
        };
        for (Uri uri : insertUris) {
            try {
                cr.insert(uri, dummyValues);
            } catch (Exception e) {
                assertTrue("Check that exception was thrown on delete for uri " + uri, true);
                continue;
            }
            assertFalse("Delete on " + uri + " was supposed to not working", true);
        }
    }
}
