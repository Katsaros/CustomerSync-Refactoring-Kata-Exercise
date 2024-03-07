package codingdojo;

import codingdojo.model.ShoppingList;

import java.util.List;

public class ShoppingListPrinter {
    public static String printShoppingLists(List<ShoppingList> shoppingLists, String indent) {
        if (shoppingLists.size() == 0) {
            return "[]";
        }
        if (shoppingLists.size() == 1) {
            ShoppingList shoppingList = shoppingLists.get(0);
            return "[" + printShoppingList(shoppingList) + "]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (ShoppingList shoppingList : shoppingLists) {
            sb.append("\n    " + indent);
            sb.append(printShoppingList(shoppingList));

        }
        sb.append("\n" + indent + "]");
        return sb.toString();
    }

    private static String printShoppingList(ShoppingList shoppingList) {
        return shoppingList.getProducts().toString();
    }
}
