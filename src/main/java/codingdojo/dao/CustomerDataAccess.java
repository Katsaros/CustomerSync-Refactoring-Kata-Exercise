package codingdojo.dao;

import codingdojo.model.Customer;
import codingdojo.model.ShoppingList;
import static codingdojo.util.Helper.COMPANY_NUMBER;
import static codingdojo.util.Helper.EXTERNAL_ID;

public class CustomerDataAccess {

    private final CustomerDataLayer customerDataLayer;

    public CustomerDataAccess(CustomerDataLayer customerDataLayer) {
        this.customerDataLayer = customerDataLayer;
    }

    public CustomerMatches loadCompanyCustomer(String externalId, String companyNumber) {
        Customer matchByExternalId = findByExternalId(externalId);
        if (matchByExternalId != null) {
            return createCustomerMatchesForExternalId(matchByExternalId, externalId);
        }

        Customer matchByCompanyNumber = findByCompanyNumber(companyNumber);
        if (matchByCompanyNumber != null) {
            return new CustomerMatches(COMPANY_NUMBER, matchByCompanyNumber);
        }

        return new CustomerMatches();
    }

    private Customer findByExternalId(String externalId) {
        return customerDataLayer.findByExternalId(externalId);
    }

    private Customer findByCompanyNumber(String companyNumber) {
        return customerDataLayer.findByCompanyNumber(companyNumber);
    }

    private CustomerMatches createCustomerMatchesForExternalId(Customer customer, String externalId) {
        CustomerMatches matches = new CustomerMatches(EXTERNAL_ID, customer);
        Customer matchByMasterId = customerDataLayer.findByMasterExternalId(externalId);
        if (matchByMasterId != null) {
            matches.addDuplicate(matchByMasterId);
        }
        return matches;
    }


    public CustomerMatches loadPersonCustomer(String externalId) {
        Customer matchByPersonalNumber = customerDataLayer.findByExternalId(externalId);
        String matchTerm = matchByPersonalNumber != null ? EXTERNAL_ID : null;
        return new CustomerMatches(matchTerm, matchByPersonalNumber);
    }

    public Customer updateCustomerRecord(Customer customer) {
        return customerDataLayer.updateCustomerRecord(customer);
    }

    public Customer createCustomerRecord(Customer customer) {
        return customerDataLayer.createCustomerRecord(customer);
    }

    public void updateShoppingList(Customer customer, ShoppingList consumerShoppingList) {
        customer.addShoppingList(consumerShoppingList);
        customerDataLayer.updateShoppingList(consumerShoppingList);
        customerDataLayer.updateCustomerRecord(customer);
    }
}
