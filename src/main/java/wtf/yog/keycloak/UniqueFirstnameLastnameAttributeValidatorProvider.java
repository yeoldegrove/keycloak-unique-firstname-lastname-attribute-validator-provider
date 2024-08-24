package wtf.yog.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.provider.ConfiguredProvider;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.theme.Theme;
import org.keycloak.validate.AbstractStringValidator;
import org.keycloak.validate.ValidationContext;
import org.keycloak.validate.ValidationError;
import org.keycloak.validate.ValidatorConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class UniqueFirstnameLastnameAttributeValidatorProvider extends AbstractStringValidator implements ConfiguredProvider {
    public static final String ID = "unique-firstname-lastname-attribute-combination";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();
    private static final Logger logger = Logger.getLogger(UniqueFirstnameLastnameAttributeValidatorProvider.class);

    // Map to store locks per user
    private static final Map<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    // Map to store conditions for signaling when lastName is processed
    private static final Map<String, Condition> lastNameConditions = new ConcurrentHashMap<>();
    // Map to track if lastName has been processed
    private static final Map<String, Boolean> lastNameProcessedMap = new ConcurrentHashMap<>();
    // Map to track if firstName has been processed
    private static final Map<String, Boolean> firstNameProcessedMap = new ConcurrentHashMap<>();
    // Map to store the lastName after it is processed
    private static final Map<String, String> processedLastNames = new ConcurrentHashMap<>();

    public UniqueFirstnameLastnameAttributeValidatorProvider() {
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    protected void doValidate(String attributeValue, String attributeName, ValidationContext context, ValidatorConfig config) {
        KeycloakSession session = context.getSession();
        RealmModel realm = session.getContext().getRealm();
        UserProvider userProvider = session.users();

        // Get the current user from the context
        UserModel currentUser = (UserModel) context.getAttributes().get(UserModel.class.getName());
        String userId = currentUser.getId();

        // Ensure a lock exists for this user
        userLocks.putIfAbsent(userId, new ReentrantLock());
        ReentrantLock lock = userLocks.get(userId);

        // Ensure a condition exists for this user's lastName processing
        lastNameConditions.putIfAbsent(userId, lock.newCondition());
        Condition lastNameCondition = lastNameConditions.get(userId);

        lock.lock();  // Acquire lock for this specific user
        try {
            // Initialize the lastNameProcessed and firstNameProcessed flags if they don't exist
            lastNameProcessedMap.putIfAbsent(userId, false);
            firstNameProcessedMap.putIfAbsent(userId, false);

            // Fetch current Firstname and Lastname directly from UserModel
            String currentFirstName = currentUser.getFirstName();
            String currentLastName = currentUser.getLastName();

            // Use the context attributes map to store/retrieve values
            Map<String, Object> validationAttributes = context.getAttributes();

            String proposedFirstName = (String) validationAttributes.getOrDefault("proposedFirstName", currentFirstName);
            String proposedLastName = (String) validationAttributes.getOrDefault("proposedLastName", currentLastName);

            if (attributeName.equals("lastName")) {
                // Update proposedLastName with the new value
                proposedLastName = attributeValue;
                validationAttributes.put("proposedLastName", proposedLastName);

                // Store lastName in the map for future comparison
                processedLastNames.put(userId, proposedLastName);

                // Mark lastName as processed and signal all waiting threads
                lastNameProcessedMap.put(userId, true);
                lastNameCondition.signalAll();  // Signal that lastName has been processed
            } else if (attributeName.equals("firstName")) {
                // Wait until lastName has been processed
                while (!lastNameProcessedMap.get(userId)) {
                    lastNameCondition.await();  // Wait until lastNameProcessed is true
                }
                // Update proposedFirstName with the new value
                proposedFirstName = attributeValue;
                validationAttributes.put("proposedFirstName", proposedFirstName);

                // Retrieve the processed lastName for comparison
                proposedLastName = processedLastNames.get(userId);

                // Mark firstName as processed
                firstNameProcessedMap.put(userId, true);
            }

            // Log the current and proposed firstName and lastName
            // logger.infof("Current firstName: %s, lastName: %s", currentFirstName, currentLastName);
            // logger.infof("Proposed firstName: %s, lastName: %s", proposedFirstName, proposedLastName);

            // After the update, check if both proposedFirstName and proposedLastName are available
            if (proposedFirstName != null && proposedLastName != null) {
                // Check if there is any other user with the same Firstname and Lastname combination
                if (!isFirstnameLastnameUnique(proposedFirstName, proposedLastName, realm, userProvider, currentUser)) {
                    try {
                        // Retrieve the user's preferred locale from their profile settings
                        String localeString = currentUser.getFirstAttribute(UserModel.LOCALE);
                        Locale locale = localeString != null ? Locale.forLanguageTag(localeString) : session.getContext().resolveLocale(currentUser);

                        // Get the theme and retrieve the localized message
                        Theme theme = session.theme().getTheme(Theme.Type.ACCOUNT);
                        String errorMessage = theme.getMessages(locale).getProperty("attributeValidationDuplicateFirstnameLastnameCombination");

                        // Add the localized error message
                        context.addError(new ValidationError(ID, attributeName, errorMessage));
                    } catch (IOException e) {
                        logger.error("Failed to load theme for localized messages", e);
                        context.addError(new ValidationError(ID, attributeName, "An error occurred while validating the name combination."));
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // Restore interrupted status
            logger.error("Thread was interrupted while waiting for lastName to be processed", e);
        } finally {
            lock.unlock();  // Always release the lock in the finally block

            // Clean up maps for the user once both validations are complete
            if (attributeName.equals("firstName") && firstNameProcessedMap.get(userId)) {
                userLocks.remove(userId);
                lastNameConditions.remove(userId);
                lastNameProcessedMap.remove(userId);
                firstNameProcessedMap.remove(userId);
                processedLastNames.remove(userId);
            }
        }
    }

    public boolean isFirstnameLastnameUnique(String firstName, String lastName, RealmModel realm, UserProvider userProvider, UserModel currentUser) {
        // Search for users with the same Firstname and Lastname in the realm
        Stream<UserModel> usersWithSameFirstnameLastname = userProvider.searchForUserStream(realm, firstName)
            .filter(user -> lastName.equals(user.getLastName()));

        return currentUser != null
            ? usersWithSameFirstnameLastname.filter(user -> !user.getId().equals(currentUser.getId())).findAny().isEmpty()
            : usersWithSameFirstnameLastname.findAny().isEmpty();
    }

    @Override
    public String getHelpText() {
        return "Ensure unique combination of Firstname and Lastname.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }
}
