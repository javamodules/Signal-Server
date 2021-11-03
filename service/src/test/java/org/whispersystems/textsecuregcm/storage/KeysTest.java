/*
 * Copyright 2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.storage;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.whispersystems.textsecuregcm.entities.PreKey;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KeysTest {

    private Account account;
    private Keys keys;

    @ClassRule
    public static KeysDynamoDbRule dynamoDbRule = new KeysDynamoDbRule();

    private static final String ACCOUNT_NUMBER = "+18005551234";
    private static final long DEVICE_ID = 1L;

    @Before
    public void setup() {
        keys = new Keys(dynamoDbRule.getDynamoDbClient(), KeysDynamoDbRule.TABLE_NAME);

        account = mock(Account.class);
        when(account.getNumber()).thenReturn(ACCOUNT_NUMBER);
        when(account.getUuid()).thenReturn(UUID.randomUUID());
    }

    @Test
    public void testStore() {
        assertEquals("Initial pre-key count for an account should be zero",
                0, keys.getCount(account, DEVICE_ID));

        keys.store(account, DEVICE_ID, List.of(new PreKey(1, "public-key")));
        assertEquals(1, keys.getCount(account, DEVICE_ID));

        keys.store(account, DEVICE_ID, List.of(new PreKey(1, "public-key")));
        assertEquals("Repeatedly storing same key should have no effect",
                1, keys.getCount(account, DEVICE_ID));

        keys.store(account, DEVICE_ID, List.of(new PreKey(2, "different-public-key")));
        assertEquals("Inserting a new key should overwrite all prior keys for the given account/device",
                1, keys.getCount(account, DEVICE_ID));

        keys.store(account, DEVICE_ID, List.of(new PreKey(3, "third-public-key"), new PreKey(4, "fourth-public-key")));
        assertEquals("Inserting multiple new keys should overwrite all prior keys for the given account/device",
                2, keys.getCount(account, DEVICE_ID));
    }

    @Test
    public void testTakeAccount() {
        final Device firstDevice = mock(Device.class);
        final Device secondDevice = mock(Device.class);

        when(firstDevice.getId()).thenReturn(DEVICE_ID);
        when(secondDevice.getId()).thenReturn(DEVICE_ID + 1);
        when(account.getDevices()).thenReturn(Set.of(firstDevice, secondDevice));

        assertEquals(Collections.emptyMap(), keys.take(account));

        final PreKey firstDevicePreKey = new PreKey(1, "public-key");
        final PreKey secondDevicePreKey = new PreKey(2, "second-key");

        keys.store(account, DEVICE_ID, List.of(firstDevicePreKey));
        keys.store(account, DEVICE_ID + 1, List.of(secondDevicePreKey));

        final Map<Long, PreKey> expectedKeys = Map.of(DEVICE_ID, firstDevicePreKey,
                                                      DEVICE_ID + 1, secondDevicePreKey);

        assertEquals(expectedKeys, keys.take(account));
        assertEquals(0, keys.getCount(account, DEVICE_ID));
        assertEquals(0, keys.getCount(account, DEVICE_ID + 1));
    }

    @Test
    public void testTakeAccountAndDeviceId() {
        assertEquals(Optional.empty(), keys.take(account, DEVICE_ID));

        final PreKey preKey = new PreKey(1, "public-key");

        keys.store(account, DEVICE_ID, List.of(preKey, new PreKey(2, "different-pre-key")));
        assertEquals(Optional.of(preKey), keys.take(account, DEVICE_ID));
        assertEquals(1, keys.getCount(account, DEVICE_ID));
    }

    @Test
    public void testGetCount() {
        assertEquals(0, keys.getCount(account, DEVICE_ID));

        keys.store(account, DEVICE_ID, List.of(new PreKey(1, "public-key")));
        assertEquals(1, keys.getCount(account, DEVICE_ID));
    }

    @Test
    public void testDeleteByAccount() {
        keys.store(account, DEVICE_ID, List.of(new PreKey(1, "public-key"), new PreKey(2, "different-public-key")));
        keys.store(account, DEVICE_ID + 1, List.of(new PreKey(3, "public-key-for-different-device")));

        assertEquals(2, keys.getCount(account, DEVICE_ID));
        assertEquals(1, keys.getCount(account, DEVICE_ID + 1));

        keys.delete(account.getUuid());

        assertEquals(0, keys.getCount(account, DEVICE_ID));
        assertEquals(0, keys.getCount(account, DEVICE_ID + 1));
    }

    @Test
    public void testDeleteByAccountAndDevice() {
        keys.store(account, DEVICE_ID, List.of(new PreKey(1, "public-key"), new PreKey(2, "different-public-key")));
        keys.store(account, DEVICE_ID + 1, List.of(new PreKey(3, "public-key-for-different-device")));

        assertEquals(2, keys.getCount(account, DEVICE_ID));
        assertEquals(1, keys.getCount(account, DEVICE_ID + 1));

        keys.delete(account.getUuid(), DEVICE_ID);

        assertEquals(0, keys.getCount(account, DEVICE_ID));
        assertEquals(1, keys.getCount(account, DEVICE_ID + 1));
    }

    @Test
    public void testSortKeyPrefix() {
      AttributeValue got = Keys.getSortKeyPrefix(123);
      assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, 123}, got.b().asByteArray());
    }
}
