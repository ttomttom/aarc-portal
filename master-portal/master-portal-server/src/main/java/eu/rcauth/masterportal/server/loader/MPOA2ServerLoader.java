package eu.rcauth.masterportal.server.loader;

import javax.inject.Provider;

import org.apache.commons.configuration.tree.ConfigurationNode;

import eu.rcauth.masterportal.server.storage.MPOA2TConverter;
import eu.rcauth.masterportal.server.storage.MPOA2TransactionKeys;
import eu.rcauth.masterportal.server.storage.SSHKey;
import eu.rcauth.masterportal.server.storage.SSHKeyConverter;
import eu.rcauth.masterportal.server.storage.SSHKeyIdentifierProvider;
import eu.rcauth.masterportal.server.storage.SSHKeyKeys;
import eu.rcauth.masterportal.server.storage.SSHKeyStore;

import eu.rcauth.masterportal.server.MPOA2SE;
import eu.rcauth.masterportal.server.MPOA2ServiceTransaction;
import eu.rcauth.masterportal.server.storage.sql.MPOA2SQLTransactionStoreProvider;

import eu.rcauth.masterportal.server.storage.impl.SSHKeyProvider;
import eu.rcauth.masterportal.server.storage.impl.MultiSSHKeyStoreProvider;
import eu.rcauth.masterportal.server.storage.sql.SQLSSHKeyStoreProvider;

import eu.rcauth.masterportal.server.validators.GetProxyRequestValidator;

import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.OA2ServiceTransaction;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.loader.OA2ConfigurationLoader;
import edu.uiuc.ncsa.myproxy.oa4mp.oauth2.storage.OA2SQLTransactionStoreProvider;
import edu.uiuc.ncsa.myproxy.oa4mp.server.*;
import edu.uiuc.ncsa.myproxy.oa4mp.server.storage.MultiDSClientStoreProvider;
import edu.uiuc.ncsa.myproxy.oa4mp.server.admin.transactions.DSTransactionProvider;
import edu.uiuc.ncsa.myproxy.oa4mp.server.admin.transactions.OA4MPIdentifierProvider;

import edu.uiuc.ncsa.security.core.IdentifiableProvider;
import edu.uiuc.ncsa.security.core.Identifier;
import edu.uiuc.ncsa.security.core.configuration.Configurations;
import edu.uiuc.ncsa.security.core.exceptions.GeneralException;
import edu.uiuc.ncsa.security.core.util.IdentifierProvider;
import edu.uiuc.ncsa.security.core.util.MyLoggingFacade;
import edu.uiuc.ncsa.security.delegation.storage.TransactionStore;
import edu.uiuc.ncsa.security.delegation.token.TokenForge;
import edu.uiuc.ncsa.security.storage.data.MapConverter;
import edu.uiuc.ncsa.security.storage.sql.ConnectionPool;
import edu.uiuc.ncsa.security.storage.sql.ConnectionPoolProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static edu.uiuc.ncsa.myproxy.oa4mp.server.admin.transactions.OA4MPIdentifierProvider.TRANSACTION_ID;

import static edu.uiuc.ncsa.security.core.util.IdentifierProvider.SCHEME;
import static edu.uiuc.ncsa.security.core.util.IdentifierProvider.SCHEME_SPECIFIC_PART;

import static edu.uiuc.ncsa.security.oauth_2_0.OA2ConfigTags.SCOPE;
import static edu.uiuc.ncsa.security.oauth_2_0.OA2ConfigTags.SCOPES;

import static eu.rcauth.masterportal.servlet.MPOA4MPConfigTags.*;

public class MPOA2ServerLoader<T extends ServiceEnvironmentImpl>  extends OA2ConfigurationLoader<T> {

    public MPOA2ServerLoader(ConfigurationNode node) {
        super(node);
    }

    @Override
    public String getVersionString() {
        return "Master Portal OAuth2/OIDC server configuration loader version " + VERSION_NUMBER;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T createInstance() {
        try {
            // Note we suppress an unchecked cast to T
            return (T) new MPOA2SE(loggerProvider.get(),
                    getTransactionStoreProvider(),
                    getClientStoreProvider(),
                    getSSHKeyStoreProvider(),
                    getMaxAllowedNewClientRequests(),
                    getRTLifetime(),
                    getClientApprovalStoreProvider(),
                    getMyProxyFacadeProvider(),
                    getMailUtilProvider(),
                    getMP(),
                    getAGIProvider(),
                    getATIProvider(),
                    getPAIProvider(),
                    getTokenForgeProvider(),
                    getConstants(),
                    getAuthorizationServletConfig(),
                    getUsernameTransformer(),
                    getPingable(),
                    getMpp(),   // see OA2ConfigurationLoader, we suppress an unchecked assignment
                    getMacp(),  // see OA2ConfigurationLoader, we suppress an unchecked assignment
                    getClientSecretLength(),
                    getScopes(),
                    getLocalScopes(),
                    getClaimSource(),
                    getLdapConfiguration(),
                    isRefreshTokenEnabled(),
                    isTwoFactorSupportEnabled(),
                    getMaxClientRefreshTokenLifetime(),
                    getJSONWebKeys(),   // see OA2ConfigurationLoader
                    getMyProxyPassword(),
                    getMyProxyDefaultLifetime(),
                    getMaxSSHKeys(),
                    getSSHKeyScope(),
                    getAutoRegisterEndpoint(),
                    getValidators(),
                    getIssuer(),    // see OA2ConfigurationLoader
                    isUtilServerEnabled(),
                    isOIDCEnabled(),
                    getMultiJSONStoreProvider());
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | IllegalStateException e) {
            // Note that we typically don't have a server yet, so error might go in odd places.
            throw new GeneralException("Error: MasterPortal failed to start: Could not create the runtime environment", e);
        }
    }

    protected MultiSSHKeyStoreProvider<SSHKey> sshKeySP;

    public Provider<SSHKeyStore<SSHKey>> getSSHKeyStoreProvider() {
        if ( sshKeySP == null ) {
            sshKeySP = new MultiSSHKeyStoreProvider<>(cn,
                                                      isDefaultStoreDisabled(),
                                                      loggerProvider.get(),
                                                      null,
                                                      null);

            Provider<Identifier> idProv = new SSHKeyIdentifierProvider<>();

            SSHKeyProvider<SSHKey> provider = new SSHKeyProvider<>( idProv);
            SSHKeyKeys keys = new SSHKeyKeys();
            SSHKeyConverter converter = new SSHKeyConverter<>( keys, provider);

            sshKeySP.addListener( new SQLSSHKeyStoreProvider<>(cn,
                                                               getMySQLConnectionPoolProvider(),
                                                               OA4MPConfigTags.MYSQL_STORE,
                                                               converter,
                                                               provider) );

            sshKeySP.addListener( new SQLSSHKeyStoreProvider<>(cn,
                                                               getMariaDBConnectionPoolProvider(),
                                                               OA4MPConfigTags.MARIADB_STORE,
                                                               converter,
                                                               provider) );

            sshKeySP.addListener( new SQLSSHKeyStoreProvider<>(cn,
                                                               getPgConnectionPoolProvider(),
                                                               OA4MPConfigTags.POSTGRESQL_STORE,
                                                               converter,
                                                               provider) );

        }

        return sshKeySP;
    }

    /* ADDITIONAL MYPROXY SERVER CONFIGURATIONS */

    protected String getMyProxyPassword() {
        ConfigurationNode node =  Configurations.getFirstNode(cn, MYPROXY);

        return Configurations.getFirstAttribute(node, MYPROXY_PASSWORD);
    }

    protected long getMyProxyDefaultLifetime() {
        ConfigurationNode node =  Configurations.getFirstNode(cn, MYPROXY);
        ConfigurationNode lifetimeNode =  Configurations.getFirstNode(node, MYPROXY_DEFAULT_LIFETIME);
        if (lifetimeNode==null)
            throw new GeneralException("Missing "+MYPROXY_DEFAULT_LIFETIME+" in node "+node.getName());

        return Long.parseLong( lifetimeNode.getValue().toString() );
    }

    /* GETCERT REQUEST VALIDATORS */

    protected GetProxyRequestValidator[] getValidators() {

        // get the list of all validators
        ConfigurationNode mpNode =  Configurations.getFirstNode(cn, MYPROXY);
        ConfigurationNode validatorsNode =  Configurations.getFirstNode(mpNode, MYPROXY_REQ_VALIDATORS);

        if ( validatorsNode != null ) {

            // count validators
            int validatorCnt = validatorsNode.getChildrenCount( MYPROXY_REQ_VALIDATOR );
            GetProxyRequestValidator[] validators = new GetProxyRequestValidator[ validatorCnt ];
            int i = 0;

            for ( Object node : validatorsNode.getChildren( MYPROXY_REQ_VALIDATOR ) ) {

                // get the validator handler class name
                ConfigurationNode validatorNode = (ConfigurationNode) node;
                String validatorClass = Configurations.getFirstAttribute(validatorNode,
                                                                         MYPROXY_REQ_VALIDATOR_HANDLER);

                if ( validatorClass == null || validatorClass.isEmpty() ) {
                    throw new GeneralException("Invalid validator configuration! Missing validator handler!");
                } else {
                    try {

                        // create new class instance of validator
                        Class<?> k = Class.forName(validatorClass);
                        Object x = k.getDeclaredConstructor().newInstance();

                        if ( ! (x instanceof GetProxyRequestValidator) )
                            throw new Exception("Invalid validator handler " + validatorClass + " ! Every validator class should "
                                                + "implement the " + GetProxyRequestValidator.class.getCanonicalName() + " interface");

                        // cast and init class with required input
                        GetProxyRequestValidator v = (GetProxyRequestValidator) x;
                        v.init(validatorNode, loggerProvider.get());

                        // save validator
                        validators[i] = v;
                        i++;
                    } catch (Exception e)  {
                        throw new GeneralException("Invalid validator configuration! Cannot create instance of handler " + validatorClass,e);
                    }

                }
            }
            // return validators, empty or not
            return validators;
        }
        // return empty validators
        return new GetProxyRequestValidator[0];
    }

    /* CUSTOM TRANSACTION */

    public static class MPST2Provider extends DSTransactionProvider<OA2ServiceTransaction> {

        public MPST2Provider(IdentifierProvider<Identifier> idProvider) {
            super(idProvider);
        }

        @Override
        public OA2ServiceTransaction get(boolean createNewIdentifier) {
            return new MPOA2ServiceTransaction(createNewId(createNewIdentifier));
        }

    }


    @Override
    protected Provider<TransactionStore> getTSP() {
        IdentifierProvider idp = new OA4MPIdentifierProvider(SCHEME, SCHEME_SPECIFIC_PART, TRANSACTION_ID, false);
        // Note we suppress an unchecked assignment since OA4MPIdentifierProvider does not use generics
        @SuppressWarnings("unchecked")
        IdentifiableProvider tp = new MPST2Provider(idp);
        MPOA2TransactionKeys keys = new MPOA2TransactionKeys();
        // Note we suppress an uncheck assignment in the 2nd and 4th parameters
        @SuppressWarnings("unchecked")
        MPOA2TConverter<MPOA2ServiceTransaction> tc = new MPOA2TConverter<MPOA2ServiceTransaction>(keys, tp, getTokenForgeProvider().get(), getClientStoreProvider().get());
        return getTSP(tp,  tc);
    }


    @Override
    protected OA2SQLTransactionStoreProvider createSQLTSP(ConfigurationNode config,
                                                          ConnectionPoolProvider<? extends ConnectionPool> cpp,
                                                          String type,
                                                          MultiDSClientStoreProvider clientStoreProvider,
                                                          Provider<? extends OA2ServiceTransaction> tp,
                                                          Provider<TokenForge> tfp,
                                                          MapConverter converter){
        return new MPOA2SQLTransactionStoreProvider<>(config,cpp,type,clientStoreProvider,tp,tfp,converter);
    }

    /* SSH KEY CONFIGURATION */

    protected int getMaxSSHKeys() {
        MyLoggingFacade logger = loggerProvider.get();
        ConfigurationNode node =  Configurations.getFirstNode(cn, SSH_KEYS);
        String maxValue = Configurations.getFirstAttribute(node, MAX_SSH_KEYS);
        int max = -1;
        if (maxValue != null && !maxValue.isEmpty())    {
            try {
                max=Integer.parseInt(maxValue);
                logger.info("Using maximum "+max+" keys per user");
            } catch (Exception e)   {
                logger.warn("Value of " + MAX_SSH_KEYS +
                            " in node " + SSH_KEYS + " is not a valid integer");
            }
        } else {
            logger.info("No (valid) maximum keys found");
        }
        return max;
    }

    protected String getSSHKeyScope() {
        MyLoggingFacade logger = loggerProvider.get();
        ConfigurationNode node =  Configurations.getFirstNode(cn, SSH_KEYS);
        String scope = Configurations.getFirstAttribute(node, SSH_KEYS_SCOPE);
        if (scope == null)
            logger.info("No " + SSH_KEYS_SCOPE + " attribute configured in " + SSH_KEYS + " node.");
        else
            logger.info("Required " + SSH_KEYS_SCOPE + " for using ssh key API set to: " + scope);
        return scope;
    }

    /* Configuration of autoregistration endpoint */

    protected boolean getAutoRegisterEndpoint() {
        MyLoggingFacade logger = loggerProvider.get();
        // Default is false
        boolean autoRegisterEndpoint = false;
        String x = Configurations.getFirstAttribute(cn, AUTOREGISTER_ENDPOINT_ENABLED);
        if (x == null) {
            // using default: autoRegisterEndpoint == false;
            logger.info("Attribute " + AUTOREGISTER_ENDPOINT_ENABLED +
                        " is unset, autoregistration endpoint is disabled.");
        } else {
            autoRegisterEndpoint = Boolean.parseBoolean(x);
            logger.info("Autoregistration endpoint is " +
                        (autoRegisterEndpoint ? "ENABLED" : "disabled") +
                        ".");
        }
        return autoRegisterEndpoint;
    }

    /* Configuration of MasterPortal-local scopes, which aren't forwarded to the DS */
    protected Collection<String> localScopes = null;

    /**
     * Gets those scopes that are MasterPortal-local from the config file.
     * @return Collection of String containing all the local scopes, returns
     * empty (i.e. not null) collection when there aren't any local scopes.
     */
    public Collection<String> getLocalScopes()  {
        if (localScopes == null) {
            localScopes = new ArrayList<>();
            // Get the first present SCOPES node
            if (0 < cn.getChildrenCount(SCOPES)) {
                ConfigurationNode node = Configurations.getFirstNode(cn, SCOPES);
                // Get the SCOPE children, need to look for those with attribute "local"
                List kids = node.getChildren(SCOPE);
                for (int i = 0; i < kids.size(); i++) {
                    ConfigurationNode currentNode = (ConfigurationNode) kids.get(i);
                    String currentScope = (String) currentNode.getValue();

                    // scope is non-local (i.e. forwarded) by default, so only add when local="true"
                    String x = Configurations.getFirstAttribute(currentNode, SCOPE_LOCAL);
                    if (x != null && Boolean.parseBoolean(x)) {
                        info("Adding scope \""+currentScope+"\" to list of local (i.e. non-forwarded) scopes.");
                        localScopes.add(currentScope);
                    }
                }
            }
        }
        return localScopes;
    }
}
