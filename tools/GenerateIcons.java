import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Reuses the supplied artwork without recoloring or redrawing it. */
public class GenerateIcons {
    static void write(BufferedImage input,File output,int size)throws Exception {
        output.getParentFile().mkdirs();
        BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(input,0,0,size,size,null);g.dispose();
        if(!ImageIO.write(image,"png",output))throw new Exception("PNG writer unavailable");
    }
    public static void main(String[] args)throws Exception {
        BufferedImage source=ImageIO.read(new File(args[0]));
        if(source==null)throw new Exception("Cannot read supplied icon");
        File res=new File(args[1]);
        String[] density={"mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"};
        int[] sizes={48,72,96,144,192};
        for(int i=0;i<sizes.length;i++)write(source,new File(res,"mipmap-"+density[i]+"/ic_launcher.png"),sizes[i]);
        write(source,new File(res,"drawable-nodpi/ic_launcher_foreground.png"),432);
        System.out.println("Generated 5 launcher densities and adaptive foreground from supplied image.");
    }
}
