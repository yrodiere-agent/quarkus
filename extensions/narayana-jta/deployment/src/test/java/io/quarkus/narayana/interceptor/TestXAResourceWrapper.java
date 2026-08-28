package io.quarkus.narayana.interceptor;

import javax.transaction.xa.XAResource;

import org.jboss.tm.XAResourceWrapper;

public class TestXAResourceWrapper extends TestXAResource implements XAResourceWrapper {
    private final String jndiName;

    TestXAResourceWrapper(TxAssertionData txAssertionData, String jndiName) {
        super(txAssertionData);
        this.jndiName = jndiName;
    }

    @Override
    public XAResource getResource() {
        return this;
    }

    @Override
    public String getJndiName() {
        return jndiName;
    }

    @Override
    public String getProductName() {
        return null;
    }

    @Override
    public String getProductVersion() {
        return null;
    }
}
