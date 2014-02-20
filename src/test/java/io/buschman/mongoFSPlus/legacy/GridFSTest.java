/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package io.buschman.mongoFSPlus.legacy;

import static java.nio.charset.Charset.defaultCharset;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;

public class GridFSTest {

    private GridFS gridFS;
    private DB database;

    @Before
    public void setUp() {

        MongoClientURI mongoURI = new MongoClientURI("mongodb://cayman-vm:27017");
        try {
            MongoClient mongoClient = new MongoClient(mongoURI);
            database = mongoClient.getDB("DriverTest-" + System.nanoTime());
            gridFS = new GridFS(database);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid Mongo URI: " + mongoURI.getURI(), e);
        }
    }

    @After
    public void tearDown() {

        database.dropDatabase();
    }

    @Test
    public void testSmall()
            throws Exception {

        testInOut("this is a simple test");
    }

    @Test
    public void testBig()
            throws Exception {

        int target = GridFS.DEFAULT_CHUNKSIZE * 3;
        StringBuilder buf = new StringBuilder(target);
        while (buf.length() < target) {
            buf.append("asdasdkjasldkjasldjlasjdlajsdljasldjlasjdlkasjdlaskjdlaskjdlsakjdlaskjdasldjsad");
        }
        String s = buf.toString();
        testInOut(s);
    }

    void testOutStream(final String s)
            throws Exception {

        int[] start = getCurrentCollectionCounts();

        GridFSInputFile in = gridFS.createFile();
        OutputStream writeStream = in.getOutputStream();
        writeStream.write(s.getBytes(defaultCharset()), 0, s.length());
        writeStream.close();
        GridFSDBFile out = gridFS.findOne(new BasicDBObject("_id", in.getId()));
        assert (out.getId().equals(in.getId()));
        assert (out.getChunkSize() == (long) GridFS.DEFAULT_CHUNKSIZE);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        out.writeTo(bout);
        String outString = new String(bout.toByteArray(), defaultCharset());
        assert (outString.equals(s));

        out.remove();
        int[] end = getCurrentCollectionCounts();
        assertEquals(start[0], end[0]);
        assertEquals(start[1], end[1]);
    }

    @Test
    public void testOutStreamSmall()
            throws Exception {

        testOutStream("this is a simple test");
    }

    @Test
    public void testOutStreamBig()
            throws Exception {

        int target = (int) (GridFS.DEFAULT_CHUNKSIZE * 3.5);
        StringBuilder buf = new StringBuilder(target);
        while (buf.length() < target) {
            buf.append("asdasdkjasldkjasldjlasjdlajsdljasldjlasjdlkasjdlaskjdlaskjdlsakjdlaskjdasldjsad");
        }
        String s = buf.toString();
        testOutStream(s);
    }

    @Test
    public void testOutStreamBigAligned()
            throws Exception {

        int target = (GridFS.DEFAULT_CHUNKSIZE * 4);
        StringBuilder buf = new StringBuilder(target);
        while (buf.length() < target) {
            buf.append("a");
        }
        String s = buf.toString();
        testOutStream(s);
    }

    @Test
    public void testMetadata()
            throws Exception {

        GridFSInputFile in = gridFS.createFile("foo".getBytes(defaultCharset()));
        in.put("meta", 5);
        in.save();
        GridFSDBFile out = gridFS.findOne(new BasicDBObject("_id", in.getId()));
        assert (out.get("meta").equals(5));
    }

    @Test
    public void testBadChunkSize()
            throws Exception {

        byte[] randomBytes = new byte[256];
        GridFSInputFile inputFile = gridFS.createFile(randomBytes);
        inputFile.setFilename("bad_chunk_size.bin");
        try {
            inputFile.save(0);
            fail("should have received an exception about a chunk size being zero");
        } catch (MongoException e) {
            // We expect this exception to complain about the chunksize
            assertTrue(e.toString().contains("chunkSize must be greater than zero"));
        }
    }

    @Test
    public void testMultipleChunks()
            throws Exception {

        int fileSize = 1024 * 128;
        byte[] randomBytes = new byte[fileSize];
        for (int idx = 0; idx < fileSize; ++idx) {
            randomBytes[idx] = (byte) (256 * Math.random());
        }

        GridFSInputFile inputFile = gridFS.createFile(randomBytes);
        inputFile.setFilename("bad_chunk_size.bin");

        // For good measure let's save and restore the bytes
        inputFile.save(1024);
        GridFSDBFile savedFile = gridFS.findOne(new BasicDBObject("_id", inputFile.getId()));
        ByteArrayOutputStream savedFileByteStream = new ByteArrayOutputStream();
        savedFile.writeTo(savedFileByteStream);
        byte[] savedFileBytes = savedFileByteStream.toByteArray();

        assertArrayEquals(randomBytes, savedFileBytes);
    }

    @Test
    public void getBigChunkSize()
            throws Exception {

        GridFSInputFile file = gridFS.createFile("512kb_bucket");
        file.setChunkSize(file.getChunkSize() * 2);
        OutputStream os = file.getOutputStream();
        for (int i = 0; i < 1024; i++) {
            os.write(new byte[GridFS.DEFAULT_CHUNKSIZE / 1024 + 1]);
        }
        os.close();
    }

    @Test
    public void testInputStreamSkipping()
            throws Exception {

        // int chunkSize = 5;
        int chunkSize = GridFS.DEFAULT_CHUNKSIZE;
        int fileSize = (int) (7.25 * chunkSize);

        byte[] fileBytes = new byte[fileSize];
        for (int idx = 0; idx < fileSize; ++idx) {
            fileBytes[idx] = (byte) (idx % 251);
        }
        // Don't want chunks to be aligned at byte position 0

        GridFSInputFile inputFile = gridFS.createFile(fileBytes);
        inputFile.setFilename("input_stream_skipping.bin");
        inputFile.save(chunkSize);

        GridFSDBFile savedFile = gridFS.findOne(new BasicDBObject("_id", inputFile.getId()));
        InputStream inputStream = savedFile.getInputStream();

        // Quick run-through, make sure the file is as expected
        for (int idx = 0; idx < fileSize; ++idx) {
            assertEquals((byte) (idx % 251), (byte) inputStream.read());
        }

        inputStream = savedFile.getInputStream();

        long skipped = inputStream.skip(1);
        assertEquals(1, skipped);
        int position = 1;
        assertEquals((byte) (position++ % 251), (byte) inputStream.read());

        skipped = inputStream.skip(chunkSize);
        assertEquals(chunkSize, skipped);
        position += chunkSize;
        assertEquals((byte) (position++ % 251), (byte) inputStream.read());

        skipped = inputStream.skip(-1);
        assertEquals(0, skipped);
        skipped = inputStream.skip(0);
        assertEquals(0, skipped);

        skipped = inputStream.skip(3 * chunkSize);
        assertEquals(3 * chunkSize, skipped);
        position += 3 * chunkSize;
        assertEquals((byte) (position++ % 251), (byte) inputStream.read());

        // Make sure skipping works when we skip to an exact chunk boundary
        long toSkip = inputStream.available();
        skipped = inputStream.skip(toSkip);
        assertEquals(toSkip, skipped);
        position += toSkip;
        assertEquals((byte) (position++ % 251), (byte) inputStream.read());

        skipped = inputStream.skip(2 * fileSize);
        assertEquals(fileSize - position, skipped);
        assertEquals(-1, inputStream.read());
    }

    @Test
    public void testCustomFileID()
            throws IOException {

        int chunkSize = 10;
        int fileSize = (int) (3.25 * chunkSize);

        byte[] fileBytes = new byte[fileSize];
        for (int idx = 0; idx < fileSize; ++idx) {
            fileBytes[idx] = (byte) (idx % 251);
        }

        GridFSInputFile inputFile = gridFS.createFile(fileBytes);
        int id = 1;
        inputFile.setId(id);
        inputFile.setFilename("custom_file_id.bin");
        inputFile.save(chunkSize);
        assertEquals(id, inputFile.getId());

        GridFSDBFile savedFile = gridFS.findOne(new BasicDBObject("_id", id));
        InputStream inputStream = savedFile.getInputStream();

        for (int idx = 0; idx < fileSize; ++idx) {
            assertEquals((byte) (idx % 251), (byte) inputStream.read());
        }
    }

    void testInOut(final String s)
            throws Exception {

        int[] start = getCurrentCollectionCounts();

        GridFSInputFile in = gridFS.createFile(s.getBytes(defaultCharset()));
        in.save();
        GridFSDBFile out = gridFS.findOne(new BasicDBObject("_id", in.getId()));
        assert (out.getId().equals(in.getId()));
        assert (out.getChunkSize() == (long) GridFS.DEFAULT_CHUNKSIZE);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        out.writeTo(bout);
        String outString = new String(bout.toByteArray(), defaultCharset());
        assert (outString.equals(s));

        out.remove();
        int[] end = getCurrentCollectionCounts();
        assertEquals(start[0], end[0]);
        assertEquals(start[1], end[1]);
    }

    int[] getCurrentCollectionCounts() {

        int[] i = new int[2];
        i[0] = gridFS.getFilesCollection().find().count();
        i[1] = gridFS.getChunksCollection().find().count();
        return i;
    }

    @Test( expected = IllegalArgumentException.class )
    public void testRemoveWhenObjectIdIsNull() {

        ObjectId objectId = null;
        gridFS.remove(objectId);
    }

    @Test( expected = IllegalArgumentException.class )
    public void testRemoveWhenFileNameIsNull() {

        String fileName = null;
        gridFS.remove(fileName);
    }

    @Test( expected = IllegalArgumentException.class )
    public void testRemoveWhenQueryIsNull() {

        DBObject dbObjectQuery = null;
        gridFS.remove(dbObjectQuery);
    }
}
