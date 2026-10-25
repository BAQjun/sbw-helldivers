import javax.imageio.ImageIO;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;

/** Run with JDK 17 from the repository root after changing entity textures.
 * A hash mismatch at runtime keeps the original translucent rendering path. */
class BuildEntityCutoutManifest {
 public static void main(String[] args)throws Exception {
  Path root=Path.of("src/main/resources/assets/sbw_helldiver");
  var entries=new ArrayList<String>();int total=0;
  try(var paths=Files.list(root.resolve("textures/bedrock/vehicle"))) {
   for(Path p:paths.filter(p->p.toString().endsWith(".png")).sorted().toList()) {
    total++;var im=ImageIO.read(p.toFile());boolean binary=true;
    outer:for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++) {
     int a=im.getRGB(x,y)>>>24;if(a!=0 && a!=255){binary=false;break outer;}
    }
    im.flush();
    if(binary){String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
     entries.add("  \"sbw_helldiver:"+root.relativize(p).toString().replace('\\','/')+"\": \""+sha+"\"");}
   }
  }
  Path manifest=root.resolve("models/entity_cutout_textures.json");
  Files.writeString(manifest,"{\n"+String.join(",\n",entries)+"\n}\n");
  System.out.println("Binary-alpha entity textures: "+entries.size()+"/"+total);
 }
}
