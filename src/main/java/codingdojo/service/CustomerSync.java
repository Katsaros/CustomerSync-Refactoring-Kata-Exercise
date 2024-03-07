package codingdojo.service;

import codingdojo.dao.CustomerDataAccess;
import codingdojo.dao.CustomerDataLayer;
import codingdojo.dao.CustomerMatches;
import codingdojo.dto.ExternalCustomer;
import codingdojo.enumeration.CustomerType;
import codingdojo.exception.ConflictException;
import codingdojo.model.Customer;
import codingdojo.model.ShoppingList;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static codingdojo.util.Helper.COMPANY_NUMBER;
import static codingdojo.util.Helper.EXTERNAL_ID;
import static codingdojo.util.Helper.EXISTING_CUSTOMER_FOR_EXTERNAL_CUSTOMER;

public class CustomerSync {

    private final CustomerDataAccess customerDataAccess;

    public CustomerSync(CustomerDataLayer customerDataLayer) {
        this(new CustomerDataAccess(customerDataLayer));
    }

    public CustomerSync(CustomerDataAccess db) {
        this.customerDataAccess = db;
    }

    public boolean syncWithDataLayerAndCreateCustomerIfNecessary(ExternalCustomer externalCustomer) {
       Optional<CustomerMatches> customerMatches = getCustomerMatches(externalCustomer);
        if(customerMatches.isPresent() && isCustomerInCustomerMatchesValid(customerMatches.get())) {
            synchronizeAndUpdateExistingInternalCustomerWithExternalCustomer(externalCustomer, customerMatches.get());
            return false;
        } else {
            createNewInternalCustomerAndBasedOnExternalCustomer(externalCustomer);
            customerMatches.ifPresent(matches -> updateAndSynchronizeDuplicateCustomerMatches(externalCustomer, matches));
            return true;
        }
    }


    public CustomerMatches loadCompany(ExternalCustomer externalCustomer) {
        final String externalId = externalCustomer.getExternalId();
        final String companyNumber = externalCustomer.getCompanyNumber();

        CustomerMatches customerMatches = getCustomerMatchesBasedOnExternalIdAndValidateIfIsCompany(externalId, companyNumber);
        handleCompanyCustomerMismatchedInformation(externalCustomer, customerMatches);

        return customerMatches;
    }

    private void handleCompanyCustomerMismatchedInformation(ExternalCustomer externalCustomer, CustomerMatches customerMatches) {
        if (EXTERNAL_ID.equals(customerMatches.getMatchTerm())) {
            handleMismatchedCompanyNumber(externalCustomer, customerMatches);
        } else if (COMPANY_NUMBER.equals(customerMatches.getMatchTerm())) {
            handleExistingCustomerWithMatchingCompanyNumber(externalCustomer.getExternalId(), customerMatches);
        }
    }

    public CustomerMatches loadPerson(ExternalCustomer externalCustomer) {
        final String externalId = externalCustomer.getExternalId();
        CustomerMatches customerMatches = customerDataAccess.loadPersonCustomer(externalId);
        Customer existingCustomer = customerMatches.getCustomer();
        validateIfCustomerIsPersonAndUpdateNecessaryFields(externalId, customerMatches, existingCustomer);
        return customerMatches;
    }

    private void validateIfCustomerIsPersonAndUpdateNecessaryFields(String externalId, CustomerMatches customerMatches, Customer existingCustomer) {
        if (existingCustomer != null) {
            ensureCustomerIsPerson(existingCustomer, externalId);
            updateCustomerExternalIdIfNecessary(existingCustomer, externalId, customerMatches);
        }
    }

    private void ensureCustomerIsPerson(Customer customer, String externalId) {
        if (!CustomerType.PERSON.equals(customer.getCustomerType())) {
            throw new ConflictException(EXISTING_CUSTOMER_FOR_EXTERNAL_CUSTOMER + externalId + " already exists and is not a person");
        }
    }

    private void updateCustomerExternalIdIfNecessary(Customer customer,
                                                     String externalId,
                                                     CustomerMatches customerMatches) {
        if (!EXTERNAL_ID.equals(customerMatches.getMatchTerm())) {
            customer.setExternalId(externalId);
            customer.setMasterExternalId(externalId);
        }
    }


    private void handleMismatchedCompanyNumber(ExternalCustomer externalCustomer, CustomerMatches customerMatches) {
        String customerCompanyNumber = customerMatches.getCustomer().getCompanyNumber();
        if (!externalCustomer.getCompanyNumber().equals(customerCompanyNumber)) {
            invalidateCustomerMatch(customerMatches);
        }
    }

    private void handleExistingCustomerWithMatchingCompanyNumber(String externalId, CustomerMatches customerMatches) {
        Customer customer = customerMatches.getCustomer();
        String customerExternalId = customer.getExternalId();

        handleCustomerExternalIdMismatch(externalId, customerExternalId, customer.getCompanyNumber());

        customer.setExternalId(externalId);
        customer.setMasterExternalId(externalId);
        customerMatches.addDuplicate(null);
    }

    private void handleCustomerExternalIdMismatch(String externalId, String customerExternalId, String companyNumber) {
        if (customerExternalId != null && !externalId.equals(customerExternalId)) {
            String errorMessage = String.format("%s %s doesn't match external id %s instead found %s",
                    EXISTING_CUSTOMER_FOR_EXTERNAL_CUSTOMER, companyNumber, externalId, customerExternalId);
            throw new ConflictException(errorMessage);
        }
    }

    private void invalidateCustomerMatch(CustomerMatches customerMatches) {
        Customer customer = customerMatches.getCustomer();
        customer.setMasterExternalId(null);
        customerMatches.addDuplicate(customer);
        customerMatches.setCustomer(null);
        customerMatches.setMatchTerm(null);
    }

    private CustomerMatches getCustomerMatchesBasedOnExternalIdAndValidateIfIsCompany(String externalId, String companyNumber) {
        CustomerMatches customerMatches = customerDataAccess.loadCompanyCustomer(externalId, companyNumber);
        if (isCustomerNonCompany(customerMatches)) {
            throw new ConflictException(EXISTING_CUSTOMER_FOR_EXTERNAL_CUSTOMER + externalId + " already exists and is not a company");
        }
        return customerMatches;
    }

    private static boolean isCustomerNonCompany(CustomerMatches customerMatches) {
        return customerMatches.getCustomer() != null && !CustomerType.COMPANY.equals(customerMatches.getCustomer().getCustomerType());
    }

    private void createNewInternalCustomerAndBasedOnExternalCustomer(ExternalCustomer externalCustomer) {
        Customer newInternalCustomer = new Customer();
        newInternalCustomer.setExternalId(externalCustomer.getExternalId());
        newInternalCustomer.setMasterExternalId(externalCustomer.getExternalId());
        populateFieldsFromExternalCustomerToInternalCustomer(externalCustomer, newInternalCustomer);
        saveNewCustomer(newInternalCustomer);
    }

    private void synchronizeAndUpdateExistingInternalCustomerWithExternalCustomer(ExternalCustomer externalCustomer,
                                                                                  CustomerMatches customerMatches) {
        Customer internalCustomer = customerMatches.getCustomer();
        populateFieldsFromExternalCustomerToInternalCustomer(externalCustomer, internalCustomer);
        updateAndSynchronizeDuplicateCustomerMatches(externalCustomer, customerMatches);
        updateCustomer(internalCustomer);
    }

    private static boolean isCustomerInCustomerMatchesValid(CustomerMatches customerMatches) {
        return customerMatches.getCustomer() != null;
    }

    private void updateAndSynchronizeDuplicateCustomerMatches(ExternalCustomer externalCustomer, CustomerMatches customerMatches) {
        customerMatches.getDuplicates()
                .forEach(duplicate -> updateDuplicate(externalCustomer, duplicate));
    }

    private void populateFieldsFromExternalCustomerToInternalCustomer(ExternalCustomer externalCustomer, Customer customer) {
        populateBasicFieldsAndCustomerType(externalCustomer, customer);
        updateContactInfo(externalCustomer, customer);
        if (isExternalClientPersonAndBonusPointsBalanceNotUpdated(externalCustomer, customer)) {
            updateBonusPointsBalance(externalCustomer, customer);
        }
        updateRelations(externalCustomer, customer);
        updatePreferredStore(externalCustomer, customer);
    }

    private Optional<CustomerMatches> getCustomerMatches(ExternalCustomer externalCustomer) {
        Optional<CustomerMatches> customerMatches;
        if (externalCustomer.isCompany()) {
            customerMatches = Optional.ofNullable(loadCompany(externalCustomer));
        } else {
            customerMatches = Optional.ofNullable(loadPerson(externalCustomer));
        }
        return customerMatches;
    }

    private static boolean isExternalClientPersonAndBonusPointsBalanceNotUpdated(ExternalCustomer externalCustomer,
                                                                                 Customer customer) {
        return !externalCustomer.isCompany()
                && !Objects.equals(externalCustomer.getBonusPointsBalance(), customer.getBonusPointsBalance());
    }

    private void updateRelations(ExternalCustomer externalCustomer, Customer customer) {
        List<ShoppingList> consumerShoppingLists = externalCustomer.getShoppingLists();
        for (ShoppingList consumerShoppingList : consumerShoppingLists) {
            this.customerDataAccess.updateShoppingList(customer, consumerShoppingList);
        }
    }

    private void updateCustomer(Customer customer) {
        this.customerDataAccess.updateCustomerRecord(customer);
    }

    private Customer updateAndReturnCustomer(Customer customer) {
       return this.customerDataAccess.updateCustomerRecord(customer);
    }

    private void updateDuplicate(ExternalCustomer externalCustomer, Customer duplicate) {
        if (duplicate == null) {
            duplicate = createDuplicateCustomerFromExternal(externalCustomer);
        }
        duplicate.setName(externalCustomer.getName());
        saveNewOrUpdateDuplicateCustomer(duplicate);
    }

    private void saveNewOrUpdateDuplicateCustomer(Customer duplicate) {
        if (duplicate.getInternalId() == null) {
            saveNewCustomer(duplicate);
        } else {
            updateCustomer(duplicate);
        }
    }

    private Customer createDuplicateCustomerFromExternal(ExternalCustomer externalCustomer) {
        Customer duplicateCustomer = new Customer();
        duplicateCustomer.setExternalId(externalCustomer.getExternalId());
        duplicateCustomer.setMasterExternalId(externalCustomer.getExternalId());
        return duplicateCustomer;
    }

    private void updatePreferredStore(ExternalCustomer externalCustomer, Customer customer) {
        customer.setPreferredStore(externalCustomer.getPreferredStore());
    }

    private void saveNewCustomer(Customer customer) {
        this.customerDataAccess.createCustomerRecord(customer);
    }

    private Customer saveAndReturnCustomer(Customer customer) {
        return this.customerDataAccess.createCustomerRecord(customer);
    }

    private void populateBasicFieldsAndCustomerType(ExternalCustomer externalCustomer, Customer customer) {
        customer.setName(externalCustomer.getName());
        customer.setCustomerType(getCustomerType(externalCustomer));
        if (externalCustomer.isCompany()) {
            customer.setCompanyNumber(externalCustomer.getCompanyNumber());
        }
    }

    private static CustomerType getCustomerType(ExternalCustomer externalCustomer) {
        return externalCustomer.isCompany() ? CustomerType.COMPANY : CustomerType.PERSON;
    }

    private void updateBonusPointsBalance(ExternalCustomer externalCustomer, Customer customer) {
        customer.setBonusPointsBalance(externalCustomer.getBonusPointsBalance());
    }

    private void updateContactInfo(ExternalCustomer externalCustomer, Customer customer) {
        customer.setAddress(externalCustomer.getPostalAddress());
    }
}
