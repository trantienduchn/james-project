package org.apache.james.mailbox.backup.task;

import static org.apache.james.mailbox.DefaultMailboxes.INBOX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.export.api.BlobExportMechanism;
import org.apache.james.blob.export.file.FileSystemExtension;
import org.apache.james.blob.export.file.LocalFileBlobExportMechanism;
import org.apache.james.blob.memory.MemoryBlobStore;
import org.apache.james.blob.memory.MemoryDumbBlobStore;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.ArchiveService;
import org.apache.james.mailbox.backup.DefaultMailboxBackup;
import org.apache.james.mailbox.backup.MailArchivesLoader;
import org.apache.james.mailbox.backup.ZipAssert;
import org.apache.james.mailbox.backup.ZipMailArchiveRestorer;
import org.apache.james.mailbox.backup.zip.ZipArchivesLoader;
import org.apache.james.mailbox.backup.zip.Zipper;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.Task;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;


@ExtendWith(FileSystemExtension.class)
class ExportServiceTest {
    private static final String JAMES_HOST = "james-host";
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final Username UNKNOWN_USER = Username.fromLocalPartWithDomain("unknown", DOMAIN);
    private static final String PASSWORD = "password";
    private static final String CORRESPONDING_FILE_HEADER = "corresponding-file";
    public static final String MESSAGE_CONTENT = "MIME-Version: 1.0\r\n" +
        "Subject: test\r\n" +
        "Content-Type: text/plain; charset=UTF-8\r\n" +
        "\r\n" +
        "testmail";

    private ExportService testee;
    private InMemoryMailboxManager mailboxManager;
    private MailboxSession bobSession;
    private FakeMailContext mailetContext;

    @BeforeEach
    void setUp(FileSystem fileSystem) throws Exception {
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        bobSession = mailboxManager.createSystemSession(BOB);

        DefaultMailboxBackup backup = createMailboxBackup();
        DNSService dnsService = createDnsService();
        BlobStore blobStore = new MemoryBlobStore(new HashBlobId.Factory(), new MemoryDumbBlobStore());
        mailetContext = FakeMailContext.builder().postmaster(MailAddressFixture.POSTMASTER_AT_JAMES).build();
        BlobExportMechanism blobExport = createBlobExportMechanism(fileSystem, dnsService, blobStore);
        MemoryUsersRepository usersRepository = createUsersRepository(dnsService);

        testee = new ExportService(backup, blobStore, blobExport, usersRepository);

    }

    private MemoryUsersRepository createUsersRepository(DNSService dnsService) throws Exception {
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);


        domainList.addDomain(DOMAIN);
        usersRepository.addUser(BOB, PASSWORD);
        return usersRepository;
    }

    private BlobExportMechanism createBlobExportMechanism(FileSystem fileSystem, DNSService dnsService, BlobStore blobStore) {
        return new LocalFileBlobExportMechanism(mailetContext, blobStore, fileSystem, dnsService,
            LocalFileBlobExportMechanism.Configuration.DEFAULT_CONFIGURATION);
    }

    private DNSService createDnsService() throws UnknownHostException {
        InetAddress localHost = mock(InetAddress.class);
        when(localHost.getHostName()).thenReturn(JAMES_HOST);
        DNSService dnsService = mock(DNSService.class);
        when(dnsService.getLocalHost()).thenReturn(localHost);
        return dnsService;
    }

    private DefaultMailboxBackup createMailboxBackup() {
        ArchiveService archiveService = new Zipper();
        MailArchivesLoader archiveLoader = new ZipArchivesLoader();
        ZipMailArchiveRestorer archiveRestorer = new ZipMailArchiveRestorer(mailboxManager, archiveLoader);
        return new DefaultMailboxBackup(mailboxManager, archiveService, archiveRestorer);
    }

    @Test
    void exportUserMailboxesDataShouldReturnCompletedWhenUserDoesNotExist() {
        assertThat(testee.exportUserMailboxesData(UNKNOWN_USER).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void exportUserMailboxesDataShouldReturnCompletedWhenExistingUserWithoutMailboxes() {
        assertThat(testee.exportUserMailboxesData(BOB).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void exportUserMailboxesDataShouldReturnCompletedWhenExistingUser() throws Exception {
        createAMailboxWithAMail();

        assertThat(testee.exportUserMailboxesData(BOB).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    private ComposedMessageId createAMailboxWithAMail() throws MailboxException {
        MailboxPath bobInboxPath = MailboxPath.inbox(BOB);
        mailboxManager.createMailbox(bobInboxPath, bobSession);
        return mailboxManager.getMailbox(bobInboxPath, bobSession)
            .appendMessage(MessageManager.AppendCommand.builder()
                    .build(MESSAGE_CONTENT),
                bobSession);
    }

    @Test
    void exportUserMailboxesDataShouldProduceAnEmptyZipWhenUserDoesNotExist() {
        testee.exportUserMailboxesData(UNKNOWN_USER).block();

        assertThat(mailetContext.getSentMails())
            .element(0)
            .satisfies(sentMail -> {
                try {
                    String absoluteUrl = sentMail.getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];

                    ZipAssert.assertThatZip(new FileInputStream(absoluteUrl))
                        .hasNoEntry();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    void exportUserMailboxesDataShouldProduceAnEmptyZipWhenExistingUserWithoutAnyMailboxes() {
        testee.exportUserMailboxesData(BOB).block();

        assertThat(mailetContext.getSentMails())
            .element(0)
            .satisfies(sentMail -> {
                try {
                    String absoluteUrl = sentMail.getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];

                    ZipAssert.assertThatZip(new FileInputStream(absoluteUrl))
                        .hasNoEntry();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    @Test
    void exportUserMailboxesDataShouldProduceAZipWithEntry() throws Exception {
        ComposedMessageId id = createAMailboxWithAMail();

        testee.exportUserMailboxesData(BOB).block();

        assertThat(mailetContext.getSentMails())
            .element(0)
            .satisfies(sentMail -> {
                try {
                    String absoluteUrl = sentMail.getMsg().getHeader(CORRESPONDING_FILE_HEADER)[0];

                    ZipAssert.assertThatZip(new FileInputStream(absoluteUrl))
                        .containsOnlyEntriesMatching(
                            ZipAssert.EntryChecks.hasName(INBOX + "/").isDirectory(),
                            ZipAssert.EntryChecks.hasName(id.getMessageId().serialize()).hasStringContent(MESSAGE_CONTENT));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }
}