import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

import sa.ANIMEncoder;
import sa.BitmapImage;
import sa.SEQDecoder;
import sa.SEQFrame;
import sa.SEQMovieTrack;

/**
 * seq2anim
 */
public class seq2anim
{
	public static void main(String[] args) throws IOException, Exception
	{
		if(args.length<2)
			throw new Exception("Usage: <in.seq> <out.anim>");

		convertToANIM(new File(args[0]), new File(args[1]), true);
	}

    public static void convertToANIM(File inFile, File outFile, boolean variableFramerate) throws IOException
    {
        ImageInputStream in = null;
        try
		{
            in = new FileImageInputStream(inFile);
            SEQDecoder decoder = new SEQDecoder(in);
            SEQMovieTrack track = new SEQMovieTrack();
            decoder.produce(track, false);
            if(variableFramerate)
				removeDuplicateFrames(track);

            ANIMEncoder encoder = new ANIMEncoder();
            encoder.write(outFile, track, 0x11000);
        } finally
		{
            if(in!=null)
                in.close();
		}
    }

    private static int removeDuplicateFrames(SEQMovieTrack track)
	{
        int width = track.getWidth();
        int height = track.getHeight();

        SEQFrame f0 = track.getFrame(0);
        BitmapImage bmp = new BitmapImage(width, height, track.getNbPlanes(), f0.getColorModel());
        bmp.setPreferredChunkyColorModel(f0.getColorModel());
        byte[] previousBmp = new byte[bmp.getBitmap().length];
        int[] previousColors = new int[16];
        int[] colors = new int[16];

        int removed = 0;
        SEQFrame previousF = f0;
        for (int i = 1, n = track.getFrameCount(); i < n; i++)
		{
            SEQFrame f = track.getFrame(i);
            f.decode(bmp, track);

            ((IndexColorModel) f.getColorModel()).getRGBs(colors);
            if (Arrays.equals(bmp.getBitmap(), previousBmp) && Arrays.equals(colors, previousColors))
			{
               	previousF.setRelTime(previousF.getRelTime() + f.getRelTime());
               	track.removeFrame(i);
               	--n;
               	--i;
               	++removed;
                continue;
            }
			else
			{
               	System.arraycopy(colors, 0, previousColors, 0, 16);
               	System.arraycopy(bmp.getBitmap(), 0, previousBmp, 0, previousBmp.length);
            }

            previousF = f;
        }
		
        return removed;
    }
}