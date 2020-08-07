/*
 * @(#)ANIMEncoder.java  1.0  2010-12-26
 * 
 * Copyright © 2010 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 * 
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package sa;

import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.stream.FileImageOutputStream;

/**
 * {@code ANIMEncoder}.
 * <p>
 * Reference:<br>
 * Commodore-Amiga, Inc. (1991) Amiga ROM Kernel Reference Manual. Devices.
 * Third Edition. Reading: Addison-Wesley.
 *
 * @author Werner Randelshofer
 * @version 1.0 2010-12-26 Created.
 */
public class ANIMEncoder {

    private int jiffies = 60;
    //private boolean debug = false;

    public ANIMEncoder() {
    }

    public void write(File f, BitmapImage img, int camg) throws IOException {
        IFFOutputStream out = null;
        try {
            out = new IFFOutputStream(new FileImageOutputStream(f));

            out.pushCompositeChunk("FORM", "ILBM");
            writeBMHD(out, img);
            writeCMAP(out, img);
            writeCAMG(out, camg);
            writeBODY(out, img);
            out.popChunk();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    public void write(File f, SEQMovieTrack track, int camg) throws IOException {
        if (track.getFrameCount() == 0) {
            return;
        }

        IFFOutputStream out = null;
        try {
            out = new IFFOutputStream(new FileImageOutputStream(f));
            BitmapImage img = new BitmapImage(track.getWidth(), track.getHeight(), track.getNbPlanes(), track.getFrame(0).getColorModel());
            BitmapImage oddPrev = new BitmapImage(track.getWidth(), track.getHeight(), track.getNbPlanes(), track.getFrame(0).getColorModel());
            BitmapImage evenPrev = new BitmapImage(track.getWidth(), track.getHeight(), track.getNbPlanes(), track.getFrame(0).getColorModel());

            out.pushCompositeChunk("FORM", "ANIM");

            // Write first frame as a bitmap image
            int absTime = 0;
            SEQFrame fr = track.getFrame(0);
            fr.decode(img, track);

            out.pushCompositeChunk("FORM", "ILBM");
            writeBMHD(out, img);
            writeCMAP(out, img);
            absTime += fr.getRelTime();
            writeANHD(out, track, track.getFrame(0), 0, absTime); // opDirect
            writeCAMG(out, camg);
            writeBODY(out, img);
            out.popChunk();

            // Write frames as delta image + 2 wrap up frames
            System.arraycopy(img.getBitmap(), 0, evenPrev.getBitmap(), 0, evenPrev.getBitmap().length);
            System.arraycopy(img.getBitmap(), 0, oddPrev.getBitmap(), 0, oddPrev.getBitmap().length);
            for (int i = 1, n = track.getFrameCount() + 2; i < n; ++i) {
                int index = i % track.getFrameCount();
                fr = track.getFrame(index);
                BitmapImage prev = (index & 1) == 0 ? evenPrev : oddPrev; // double buffered previous
                BitmapImage immPrev = (index & 1) == 0 ? oddPrev : evenPrev; // immediate previous
                fr.decode(img, track);
                img.setPlanarColorModel(fr.getColorModel());
                out.pushCompositeChunk("FORM", "ILBM");
                absTime += fr.getRelTime();
                writeANHD(out, track, fr, 0x5, absTime); // byteVerticalDeltaMode

                writeCMAP(out, img, immPrev);
                writeDLTA(out, img, prev);
                System.arraycopy(img.getBitmap(), 0, prev.getBitmap(), 0, prev.getBitmap().length);
                prev.setPlanarColorModel(img.getPlanarColorModel());
                out.popChunk();
            }

            out.popChunk();
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
 
    /**
     * Writes the bitmap header (ILBM BMHD).
     *
     * <pre>
     * typedef UBYTE Masking; // Choice of masking technique
     *
     * #define mskNone                 0
     * #define mskHasMask              1
     * #define mskHasTransparentColor  2
     * #define mskLasso                3
     *
     * typedef UBYTE Compression; // Choice of compression algorithm
     *     // applied to the rows of all source and mask planes.
     *     // "cmpByteRun1" is the byte run encoding. Do not compress
     *     // accross rows!
     * #define cmpNone      0
     * #define cmpByteRun1  1
     *
     * typedef struct {
     *   UWORD       w, h; // raster width & height in pixels
     *   WORD        x, y; // pixel position for this image
     *   UBYTE       nbPlanes; // # source bitplanes
     *   Masking     masking;
     *   Compression compression;
     *   UBYTE       pad1;     // unused; ignore on read, write as 0
     *   UWORD       transparentColor; // transparent "color number" (sort of)
     *   UBYTE       xAspect, yAspect; // pixel aspect, a ratio width : height
     *   UWORD       pageWidth, pageHeight; // source "page" size in pixels
     *   } BitmapHeader;
     * </pre>
     */
    private void writeBMHD(IFFOutputStream out, BitmapImage img) throws IOException {
        out.pushDataChunk("BMHD");
        out.writeUWORD(img.getWidth());
        out.writeUWORD(img.getHeight());
        out.writeWORD(0);
        out.writeWORD(0);
        out.writeUBYTE(img.getDepth());
        out.writeUBYTE(0); // mskNone
        out.writeUBYTE(1); // cmpByteRun1
        out.writeUBYTE(0);
        out.writeUWORD(0);
        out.writeUBYTE(44);
        out.writeUBYTE(52);
        out.writeUWORD(img.getWidth());
        out.writeUWORD(img.getHeight());
        out.popChunk();
    }

    /**
     * Writes the color map (ILBM CMAP).
     */
    private void writeCMAP(IFFOutputStream out, BitmapImage img) throws IOException {
        out.pushDataChunk("CMAP");

        IndexColorModel cm = (IndexColorModel) img.getPlanarColorModel();
        for (int i = 0, n = cm.getMapSize(); i < n; ++i) {
            out.writeUBYTE(cm.getRed(i));
            out.writeUBYTE(cm.getGreen(i));
            out.writeUBYTE(cm.getBlue(i));
        }

        out.popChunk();
    }

    /**
     * Writes the color map (ILBM CMAP) if it is different from the previous image.
     */
    private void writeCMAP(IFFOutputStream out, BitmapImage img, BitmapImage prev) throws IOException {
        IndexColorModel cm = (IndexColorModel) img.getPlanarColorModel();
        IndexColorModel prevCm = (IndexColorModel) prev.getPlanarColorModel();

        boolean equals = true;
        for (int i = 0, n = cm.getMapSize(); i < n; ++i) {
            if (cm.getRGB(i) != prevCm.getRGB(i)) {
                equals = false;
                break;
            }
        }
        if (!equals) {
            writeCMAP(out, img);
        }
    }

    /**
     * Writes the color amiga viewport mode display id (ILBM CAMG).
     */
    private void writeCAMG(IFFOutputStream out, int camg) throws IOException {
        out.pushDataChunk("CAMG");

        out.writeLONG(camg);

        out.popChunk();
    }

    /**
     * Writes the body (ILBM BODY).
     */
    private void writeBODY(IFFOutputStream out, BitmapImage img) throws IOException {
        out.pushDataChunk("BODY");

        int widthInBytes = (img.getWidth() + 7) / 8;
        int ss = img.getScanlineStride();
        int bs = img.getBitplaneStride();

        int offset = 0;

        byte[] data = img.getBitmap();

        for (int y = 0, h = img.getHeight(); y < h; y++) {
            for (int p = 0, d = img.getDepth(); p < d; p++) {
                out.writeByteRun1(data, offset + bs * p, widthInBytes);
            }
            offset += ss;
        }

        out.popChunk();
    }

    /**
     * Writes a delta frame (ILBM DLTA) with "byte vertical" (method 5).
     *
     * <p>
     * The DLTA chunk for method 5 has 16 long pointers at the start.
     * The first 8 are pointers to the start of the data for each of the
     * bitplanes (up to a max of 8 planes). The second set of 8 are not used.
     * <p>
     * Compression/decompression is performed on a plane-by-plane basis.
     * Each column is compressed separately. A 320x200 bitplane would have 40
     * columns of 200 bytes each. Each column starts with an op-count followed
     * by a number of ops. If the op-count is zero, that's ok, it just means
     * there's no change in this column from the last frame. If there is only
     * one list of pointers ops for all planes, then the pointer to that list
     * is repeated in all positions so the playback code need not even be
     * aware of it.In fact, one could get fancy and have some bitplanes share
     * lists while others have different lists, or no lists (the problem in
     * these schemes lie in the generation, not in the playback).
     * <p>
     * The ops are of three classes, and followed by a varying amount of data
     * depending on which class:
     * <ol>
     * <li>Skip ops - this is a byte whith the hi bit clear that says how
     * many rows to move the "dest" pointer forward, ie to skip. It is non-zero.</li>
     * <li>Uniq ops - this is a byte with the hi bit set. The hi bit is masked
     * down and the remainder is a count of the number of bytes of data to copy
     * literally. It's followed by the data to copy.</li>
     * <li>Same ops - this is a 0 byte followed by a count byte, followed by a
     * byte value to repeat count times.</li>
     * </ol>
     * <p>
     * Do bear in mind that the data is compressed vertically rather than
     * horizontally, so to get to the next byte in the destination we add the
     * number of bytes per row instead of one.
     * <p>
     * Reference:<br>
     * Commodore-Amiga, Inc. (1991) Amiga ROM Kernel Reference Manual. Devices.
     * Third Edition. Reading: Addison-Wesley.
     * Pages 445 - 449.
     */
    private void writeDLTA(IFFOutputStream out, BitmapImage img, BitmapImage prev) throws IOException {
        out.pushDataChunk("DLTA");

        int height = img.getHeight();
        int widthInBytes = (img.getWidth() + 7) / 8;
        int ss = img.getScanlineStride();
        int bs = img.getBitplaneStride();

        //int offset = 0;

        byte[] data = img.getBitmap();
        byte[] prevData = prev.getBitmap();
        SeekableByteArrayOutputStream buf = new SeekableByteArrayOutputStream();

        // Buffers for a theoretical maximum of 16 planes.
        byte[][] planes = new byte[16][0];



        // Repeat for each plane.
        int depth = img.getDepth();
        for (int p = 0; p < depth; ++p) {
            buf.reset();

            // Each column of the plane is compressed separately.
            for (int column = 0; column < widthInBytes; ++column) {
                writeByteVertical(buf, data, prevData, bs * p + column, height, ss);
            }

            planes[p] = buf.toByteArray();

            if (planes[p].length == widthInBytes) {
                // => all columns have an op-count of 0. We can drop them entirely.
                planes[p] = new byte[0];
            }
        }

        // pPointers are the pointers (index) to the op-codes of each plane.
        int[] pPointers = new int[16];

        // Compute pointers. If two planes have the same delta, we only store
        // the delta once.
        for (int p = 0; p < depth; ++p) {
            if (planes[p].length == 0) {
                pPointers[p] = 0;
            } else {
                pPointers[p] = 16 * 4;
                for (int q = 0; q < p; ++q) {
                    if (Arrays.equals(planes[q], planes[p])) {
                        pPointers[p] = pPointers[q];
                        planes[p] = new byte[0];
                        break;
                    }
                    pPointers[p] += planes[q].length;
                }
            }
        }


        // write pointers for each bitmap plane
        for (int p = 0; p < pPointers.length; ++p) {
            out.writeULONG(pPointers[p]);
        }
        // write deltas
        for (int p = 0; p < planes.length; ++p) {
            out.write(planes[p]);
        }


        out.popChunk();
    }

    /**
     * Encodes a column of an image with the "byte vertical" method (method 5).
     *
     * @param out
     * @param data
     * @param prev
     * @param offset
     * @param length
     * @param step
     * @throws IOException
     */
    private void writeByteVertical(SeekableByteArrayOutputStream out, byte[] data, byte[] prev, int offset, int length, int step) throws IOException {
        int opCount = 0;

        // Reserve space for opCount in the stream
        long opCountPos = out.getStreamPosition();
        out.write(0);

        // Start offset of the literal run
        int literalOffset = 0;
        int i;
        for (i = 0; i < length; i++) {
            // Count skips
            int skipCount = i;
            for (; skipCount < length; skipCount++) {
                if (data[offset + skipCount * step] != prev[offset + skipCount * step]) {
                    break;
                }
            }
            skipCount = skipCount - i;

            // Can we skip until the end?
            if (skipCount + i == length) {
                break;
            }

            if (skipCount > 0 && literalOffset == i
                    || skipCount > 1) {
                // Flush the literal run, if we have one
                if (literalOffset < i) {
                    opCount++;
                    out.write(0x80 | (i - literalOffset)); // Write Uniq Op
                    for (int j = literalOffset; j < i; j++) {
                        out.write(data[offset + j * step]);
                    }
                }

                // Write the skip count
                i += skipCount - 1;
                literalOffset = i + 1;
                for (; skipCount > 127; skipCount -= 127) {
                    opCount++;
                    out.write(127); // Write Skip Op
                }
                opCount++;
                out.write(skipCount); // Write Skip Op
            } else {
                // Read a byte
                byte b = data[offset + i * step];

                // Count repeats of that byte
                int repeatCount = i + 1;
                for (; repeatCount < length; repeatCount++) {
                    if (data[offset + repeatCount * step] != b) {
                        break;
                    }
                }
                repeatCount = repeatCount - i;

                if (repeatCount == 1) {
                    // Flush the literal run, if it gets too large
                    if (i - literalOffset > 126) {
                        opCount++;
                        out.write(0x80 | (i - literalOffset)); // Write Uniq Op
                        for (int j = literalOffset; j < i; j++) {
                            out.write(data[offset + j * step]);
                        }
                        literalOffset = i;
                    }

                    // If the byte repeats less than 4 times, and we have a literal
                    // run with enough space, add it to the literal run
                } else if (repeatCount < 4
                        && literalOffset < i && i - literalOffset < 126) {
                    i++;
                } else {
                    // Flush the literal run, if we have one
                    if (literalOffset < i) {
                        opCount++;
                        out.write(0x80 | (i - literalOffset)); // Write Uniq Op
                        for (int j = literalOffset; j < i; j++) {
                            out.write(data[offset + j * step]);
                        }
                    }
                    // Write the repeat run
                    i += repeatCount - 1;
                    literalOffset = i + 1;
                    // We have to write multiple runs, if the byte repeats more
                    // than 256 times.
                    for (; repeatCount > 255; repeatCount -= 255) {
                        opCount++;
                        out.write(0);
                        out.write(255); // Write Same Op
                        out.write(b);
                    }
                    opCount++;
                    out.write(0);
                    out.write(repeatCount); // Write Same Op
                    out.write(b);
                }
            }
        }

        // Flush the literal run, if we have one
        if (literalOffset < i) {
            opCount++;
            out.write(0x80 | (i - literalOffset)); // Write Uniq Op
            for (int j = literalOffset; j < i; j++) {
                out.write(data[offset + j * step]);
            }
        }

        // Write the opCount
        long pos = out.getStreamPosition();
        out.seek(opCountPos);
        out.write(opCount);
        out.seek(pos);
    }


    /**
     * Decodes the anim header (ILBM ANHD).
     *
     * <pre>
     * typedef UBYTE Operation; // Choice of compression algorithm.
     *
     * #define opDirect        0  // set directly (normal ILBM BODY)
     * #define opXOR           1  // XOR ILBM mode
     * #define opLongDelta     2  // Long Delta mode
     * #define opShortDelta    3  // Short Delta Mode
     * #define opGeneralDelta  4  // Generalized short/long Delta mode
     * #define opByteVertical  5  // Byte Vertical Delta mode
     * #define opStereoDelta   6  // Stereo op 5 (third party)
     * #define opVertical7     7  // Short/Long Vertical Delta mode (opcodes and data stored separately)
     * #define opVertical8     8  // Short/Long Vertical Delta mode (opcodes and data combined)
     * #define opJ            74  // (ascii 'J') reserved for Eric Graham's compression technique
     *
     * typedef struct {
     * Operation   operation; // The compression method.
     * UBYTE       mask;      // XOR mode only - plane mask where each
     * // bit is set =1 if there is data and =0
     * // if not.
     * UWORD       w,h;       // XOR mode only - width and height of the
     * // area represented by the BODY to eliminate
     * // unnecessary un-changed data.
     * UWORD        x,y;       // XOR mode only - position of rectangular
     * // area represented by the BODY.
     * ULONG       abstime;   // currently unused - timing for a frame
     * // relative to the time the first frame
     * // was displayed - in jiffies (1/60 sec).
     * ULONG       reltime;   // timing for frame relative to time
     * // previous frame was displayed - in
     * // jiffies (1/60 sec).
     * UBYTE       interleave;// unused so far - indicates how many frames
     * // back this data is to modify. =0 defaults
     * // to indicate two frames back (for double
     * // buffering). =n indicates n frames back.
     * // The main intent here is to allow values
     * // of =1 for special applications where
     * // frame data would modify the immediately
     * // previous frame.
     * UBYTE        pad0;     // Pad byte, not used at present.
     * ULONG        bits;     // 32 option bits used by opGeneralDelta,
     * // opByteVertical, opVertical7 and opVertical8.
     * // At present only 6 are identified, but the
     * // rest are set =0 tso they can be used to
     * // implement future ideas. These are defined
     * // for opGeneralData only at this point. It is
     * // recommended that all bits be set =0 for
     * // opByteVertical and that any bit settings used in
     * // the future (such as for XOR mode) be compatible
     * // with the opGeneralData settings. Player code
     * // should check undefined bits in opGeneralData and
     * // opByteVertical to assure they are zero.
     * //
     * // The six bits for current use are:
     * //
     * // bit #    set =0          set =1
     * // =======================================
     * // 0        short data          long data
     * // 1        set                 XOR
     * // 2        separate info       one info list
     * //          for each plane      for all planes
     * // 3        not RLC             RLC (run length coded)
     * // 4        horizontal          vertical
     * // 5        short info offsets  long info offsets
     *
     * UBYTE        pad[16];  // This is a pad for future use for future
     * // compression modes.
     * } AnimHeader;
     * </pre>
     */
    private void writeANHD(IFFOutputStream out, SEQMovieTrack track, SEQFrame frame, int compressionMode, int absTime) throws IOException {
        out.pushDataChunk("ANHD");

        out.writeUBYTE(compressionMode);
        out.writeUBYTE(0);
        out.writeUWORD(track.getWidth());
        out.writeUWORD(track.getHeight());
        out.writeUWORD(0);
        out.writeUWORD(0);
        out.writeULONG(absTime * jiffies / track.getJiffies());
        out.writeULONG(frame.getRelTime() * jiffies / track.getJiffies());
        out.writeUBYTE(0);
        out.writeUBYTE(0);
        out.writeULONG(0); // bits
        out.writeULONG(0); // pad
        out.writeULONG(0);
        out.writeULONG(0);
        out.writeULONG(0);
        out.popChunk();
    }

}
