package info.piwai.buildergen.bean;

import org.junit.Test;

import static org.junit.Assert.assertSame;

public class CustomFactoryMethodTest {

    @Test
    public void factoryMethodTest() {
        String testString = "Test";
        CustomFactoryMethodBean cfmb = CustomFactoryMethodBeanBuilder.custom()
                .testString(testString)
                .build();

        assertSame(testString, cfmb.getTestString());
    }
}
