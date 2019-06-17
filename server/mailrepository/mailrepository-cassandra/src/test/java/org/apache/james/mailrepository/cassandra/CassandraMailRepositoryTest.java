/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailrepository.cassandra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.util.Optional;

import javax.mail.internet.MimeMessage;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.CassandraRestartExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.cassandra.CassandraBlobModule;
import org.apache.james.blob.cassandra.CassandraBlobsDAO;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.mailrepository.MailRepositoryContract;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.server.core.MailImpl;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;

@ExtendWith(CassandraRestartExtension.class)
class CassandraMailRepositoryTest implements MailRepositoryContract {
    static final MailRepositoryUrl URL = MailRepositoryUrl.from("proto://url");
    static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMailRepositoryModule.MODULE,
            CassandraBlobModule.MODULE));

    private CassandraMailRepository cassandraMailRepository;
    private CassandraMailRepositoryCountDAO countDAO;
    private MergingCassandraMailRepositoryMailDao mailDAO;
    private Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private CassandraMailRepositoryKeysDAO keysDAO;


    @BeforeEach
    void setup(CassandraCluster cassandra) {
        CassandraMailRepositoryMailDAO v1 = new CassandraMailRepositoryMailDAO(cassandra.getConf(), BLOB_ID_FACTORY, cassandra.getTypesProvider());
        CassandraMailRepositoryMailDaoV2 v2 = new CassandraMailRepositoryMailDaoV2(cassandra.getConf(), BLOB_ID_FACTORY);
        mailDAO = spy(new MergingCassandraMailRepositoryMailDao(v1, v2));
        keysDAO = spy(new CassandraMailRepositoryKeysDAO(cassandra.getConf(), CassandraUtils.WITH_DEFAULT_CONFIGURATION));
        countDAO = spy(new CassandraMailRepositoryCountDAO(cassandra.getConf()));
        CassandraBlobsDAO blobsDAO = new CassandraBlobsDAO(cassandra.getConf());
        mimeMessageStore = MimeMessageStore.factory(blobsDAO).mimeMessageStore();

        cassandraMailRepository = new CassandraMailRepository(URL, keysDAO, countDAO, mailDAO, mimeMessageStore);
    }

    @Override
    public MailRepository retrieveRepository() {
        return cassandraMailRepository;
    }

    @Test
    @Disabled("key is unique in Cassandra")
    @Override
    public void sizeShouldBeIncrementedByOneWhenDuplicates() {
    }

    @Test
    void storeFailsBecauseOfIncrementingCountLeadToInconsistentSize() throws Exception {
        doThrow(new RuntimeException("mocked exception"))
            .when(countDAO).increment(any());

        MailImpl mail = createMail(MAIL_1);
        try {
            cassandraMailRepository.store(mail);
        } catch (RuntimeException e) {
            // mocked exception
        }

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(cassandraMailRepository.list())
                .containsExactly(MAIL_1);
            softly.assertThat(cassandraMailRepository.size())
                .isEqualTo(0);
        });
    }

    @Test
    void removeFailsBecauseOfDecrementingCountShouldNotLeadToInconsistentSize() throws Exception {
        doThrow(new RuntimeException("mocked exception"))
            .when(countDAO).decrement(any());

        MailImpl mail = createMail(MAIL_1);
        cassandraMailRepository.store(mail);

        try {
            cassandraMailRepository.remove(mail);
        } catch (RuntimeException e) {
            // mocked exception, cannot remove mail from mail repository
        }

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(cassandraMailRepository.list())
                .containsExactly(MAIL_1);
            softly.assertThat(cassandraMailRepository.size())
                .isEqualTo(1);
        });
    }

    @Test
    void mailDAOStoreFailsLeadToRedundantData() throws Exception {
        ArgumentCaptor<BlobId> headerBlobIdCaptor = ArgumentCaptor.forClass(BlobId.class);
        ArgumentCaptor<BlobId> bodyBlobIdCaptor = ArgumentCaptor.forClass(BlobId.class);

        doThrow(new RuntimeException("mocked exception"))
            .when(mailDAO).store(any(), any(), headerBlobIdCaptor.capture(), bodyBlobIdCaptor.capture());

        MailImpl mail = createMail(MAIL_1);
        try {
            cassandraMailRepository.store(mail);
        } catch (RuntimeException e) {
            // mocked exception
        }

        MimeMessagePartsId parts = MimeMessagePartsId.builder()
            .headerBlobId(headerBlobIdCaptor.getValue())
            .bodyBlobId(bodyBlobIdCaptor.getValue())
            .build();

        assertThat(mimeMessageStore.read(parts).block())
            .isNotNull();
    }

    @Test
    void countDAOIncrementFailsLeadToRedundantData() throws Exception {
        doThrow(new RuntimeException("mocked exception"))
            .when(countDAO).increment(any());

        MailImpl mail = createMail(MAIL_1);
        try {
            cassandraMailRepository.store(mail);
        } catch (RuntimeException e) {
            // mocked exception
        }

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(keysDAO.list(URL).toStream())
                .containsExactly(MAIL_1);

            Optional<CassandraMailRepositoryMailDaoAPI.MailDTO> mailDTO = mailDAO.read(URL, MAIL_1).block();
            softly.assertThat(mailDTO).isNotEmpty();

            softly.assertThat(cassandraMailRepository.retrieve(MAIL_1))
                .satisfies(retrievedMail -> assertThat(retrievedMail.getName())
                    .isEqualTo(MAIL_1.asString()));
        });
    }
}