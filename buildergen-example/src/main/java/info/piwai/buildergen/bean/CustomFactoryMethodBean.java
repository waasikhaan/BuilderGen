package info.piwai.buildergen.bean;

import info.piwai.buildergen.api.Buildable;

@Buildable(factoryMethod = "custom")
public class CustomFactoryMethodBean {
    private final String testString;

    public CustomFactoryMethodBean(String testString) {
        this.testString = testString;
    }

    public String getTestString() {
        return testString;
    }
}
