package blue.endless.jxnb;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.junit.Test;

public class TestBasic {
	
	
	@Test
	public void loadTexture() throws IOException {
		File f = new File("run", "HeadIdle0000.xnb");
		FileInputStream in = new FileInputStream(f);
		XNBInputStream str = new XNBInputStream(in);
		str.close();
	}
}
