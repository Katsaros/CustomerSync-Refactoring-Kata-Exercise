package codingdojo.enumeration;

public enum CustomerType {
    PERSON("Person"),
    COMPANY("Company");

    private final String label;

    CustomerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}