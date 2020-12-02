package oy.learn.nio;

import org.junit.Test;

import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;

/**
 * @author huangyang
 * @Description: ${todo}(这里用一句话描述这个类的作用)
 * @date 2018/12/26 下午5:24
 */
public class SelectProviderTest {

    /**
     * 验证SelectorProvider 只有一个实例 Selector可以有多个
     * @throws IOException
     */
    @Test
    public void test_provider() throws IOException {
        for (int i = 0 ; i<2; i++){
            SelectorProvider provider = SelectorProvider.provider();
            System.out.println(provider);
            System.out.println(provider.openSelector());
        }
    }


    @Test
    public void test_or(){
        int c = 17 , b = 5 , d = 22;
        int a = c | b  ;
        System.out.println(a & c);
        System.out.println(a & b);
    }

}
