
public class Medicine {
    private int id;
    private String name;
    private String brand;
    private double price;
    private int stockQuantity;

    public Medicine() {}

    public Medicine(int id, String name, String brand, double price, int stockQuantity) {
        this.id = id;
        this.name = name;
        this.brand = brand;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }


    public String toJson() {
        return String.format(
            java.util.Locale.US,
            "{\"id\":%d,\"name\":\"%s\",\"brand\":\"%s\",\"price\":%.2f,\"stockQuantity\":%d}",
            id, escapeJson(name), escapeJson(brand), price, stockQuantity
        );
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
