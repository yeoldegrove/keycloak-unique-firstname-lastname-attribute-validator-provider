# Keycloak Unique Firstname/Lastname Combination Attribute Validator Provider

This repository contains the Keycloak Unique Firstname/Lastname Combination Attribute Validator Provider, designed to enhance your Keycloak instance by enabling the validation of unique combinations of firstname and lastname attributes in user profiles (only one user can have the same firstname/lastname combination). Below are the steps to set up and use this provider in your Keycloak environment.

## Prerequisites

Before you begin, ensure that the `User Profile` feature is enabled in Keycloak. By default, this feature is not active. To enable it, you must start Keycloak with a specific feature flag.

## Enabling User Profile in Keycloak

To enable the `User Profile` feature:

1. Run Keycloak with the `--features=declarative-user-profile` flag. This setting is already included in the `docker-compose` file provided with this repository.
2. Start the project using the following command:

```bash
docker compose up -d
```

3. Once the Keycloak instance is up and running, navigate to the admin console.

### Accessing the Admin Console

1. Go to the Keycloak admin console.
2. Log in using the credentials:
   - **Username:** admin
   - **Password:** admin
3. Select your realm.

### Enabling User Profile

1. In the realm, navigate to `Realm Settings -> General`.
2. Enable `User Profile Enabled`.

## Configuring the Unique Firstname-Lastname Validator

After enabling the User Profile feature:

1. Go to `Realm Settings -> User Profile`.
2. Add the `firstname` and `lastname` attributes to the profile.
3. Add the validator named `unique-firstname-lastname-attribute-combination` to these attributes.

Once these steps are completed, your Keycloak instance will be configured to validate unique combinations of `firstname` and `lastname` attributes in user profiles, using the Unique Firstname-Lastname Attribute Validator Provider.

## Customizing Error Messages

The error message shown when a duplicate firstname-lastname combination is detected can be customized for different locales. Keycloak uses a theme-based localization system to handle messages.

### Steps to Customize Error Messages:

1. Navigate to your Keycloak theme directory (e.g., `$KEYCLOAK_HOME/themes/your-theme/account`).
2. Locate or create the `messages` directory.

   Example structure for `account` sub-theme:
   ```
   $your-theme/
   └── account/
       └── messages/
           ├── messages_en.properties
           └── messages_de.properties
   ```

3. In the `messages_en.properties` file (or other locale-specific files), add the following entry to customize the error message:
   ```properties
   attributeValidationDuplicateFirstnameLastnameCombination = Firstname and Lastname combination must be unique.
   ```
   You can customize this message for different languages by adding it to the appropriate locale file (e.g., `messages_de.properties` for German).

4. Ensure that your custom theme is active for the specific realm where you want to apply the changes.

### Activating the Custom Theme:

1. In the Keycloak admin console, go to `Realm Settings -> Themes`.
2. Set the **Account Theme** (or **Login Theme**, depending on your use case) to your custom theme.
3. Save the settings.

### Example Usage:

Once the error message is configured, when a user attempts to register or update their profile with a duplicate firstname and lastname combination (another user already has this combination), they will see the localized error message you defined.

## Support

If you encounter any issues or have questions, please file an issue in this repository's issue tracker.
