package edu.unc.mapseq.commons.bergmips.vc;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.renci.common.exec.BashExecutor;
import org.renci.common.exec.CommandInput;
import org.renci.common.exec.CommandOutput;
import org.renci.common.exec.Executor;
import org.renci.common.exec.ExecutorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.unc.mapseq.dao.MaPSeqDAOBeanService;
import edu.unc.mapseq.dao.model.MimeType;
import edu.unc.mapseq.dao.model.WorkflowRun;
import edu.unc.mapseq.module.sequencing.freebayes.FreeBayes;
import edu.unc.mapseq.module.sequencing.picard2.PicardCollectHsMetrics;
import edu.unc.mapseq.module.sequencing.picard2.PicardMergeSAM;
import edu.unc.mapseq.module.sequencing.samtools.SAMToolsIndex;
import edu.unc.mapseq.workflow.WorkflowBeanService;
import edu.unc.mapseq.workflow.sequencing.IRODSBean;

public class RegisterToIRODSRunnable implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RegisterToIRODSRunnable.class);

    private MaPSeqDAOBeanService mapseqDAOBeanService;

    private String subjectName;

    private String subjectMergeHome;

    private WorkflowRun workflowRun;

    public RegisterToIRODSRunnable() {
        super();
    }

    public RegisterToIRODSRunnable(MaPSeqDAOBeanService mapseqDAOBeanService, WorkflowRun workflowRun, String subjectName, String subjectMergeHome) {
        super();
        this.mapseqDAOBeanService = mapseqDAOBeanService;
        this.workflowRun = workflowRun;
        this.subjectName = subjectName;
        this.subjectMergeHome = subjectMergeHome;
    }

    @Override
    public void run() {
        logger.debug("ENTERING run()");

        try {

            File subjectMergeDirectory = new File(subjectMergeHome, subjectName);
            File tmpDir = new File(subjectMergeDirectory, "tmp");
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }

            String irodsDirectory = String.format("/MedGenZone/%s/sequencing/bergmips/subjectMerge/%s", workflowRun.getWorkflow().getSystem().getValue(),
                    subjectName);

            CommandOutput commandOutput = null;

            List<CommandInput> commandInputList = new LinkedList<CommandInput>();

            CommandInput commandInput = new CommandInput();
            commandInput.setExitImmediately(Boolean.FALSE);
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("$IRODS_HOME/imkdir -p %s%n", irodsDirectory));
            sb.append(String.format("$IRODS_HOME/imeta add -C %s Project GeneScreen%n", irodsDirectory));
            commandInput.setCommand(sb.toString());
            commandInput.setWorkDir(tmpDir);
            commandInputList.add(commandInput);

            List<IRODSBean> files2RegisterToIRODS = new ArrayList<IRODSBean>();

            File subjectDirectory = new File(subjectMergeHome, subjectName);

            BundleContext bundleContext = FrameworkUtil.getBundle(getClass()).getBundleContext();
            Bundle bundle = bundleContext.getBundle();
            String version = bundle.getVersion().toString();

            String referenceSequence = null;

            try {
                Collection<ServiceReference<WorkflowBeanService>> references = bundleContext.getServiceReferences(WorkflowBeanService.class,
                        "(osgi.service.blueprint.compname=BergMIPsVariantCallingWorkflowBeanService)");

                if (CollectionUtils.isNotEmpty(references)) {
                    for (ServiceReference<WorkflowBeanService> sr : references) {
                        WorkflowBeanService wbs = bundleContext.getService(sr);
                        if (wbs != null && MapUtils.isNotEmpty(wbs.getAttributes())) {
                            referenceSequence = wbs.getAttributes().get("referenceSequence");
                            break;
                        }
                    }
                }
            } catch (InvalidSyntaxException e) {
                e.printStackTrace();
            }

            List<ImmutablePair<String, String>> attributeList = Arrays.asList(new ImmutablePair<String, String>("ParticipantId", subjectName),
                    new ImmutablePair<String, String>("MaPSeqWorkflowVersion", version),
                    new ImmutablePair<String, String>("MaPSeqWorkflowName", workflowRun.getWorkflow().getName()),
                    new ImmutablePair<String, String>("MaPSeqSystem", workflowRun.getWorkflow().getSystem().getValue()));

            List<ImmutablePair<String, String>> attributeListWithJob = new ArrayList<>(attributeList);
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqJobName", PicardMergeSAM.class.getSimpleName()));
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqMimeType", MimeType.APPLICATION_BAM.toString()));
            File mergeBAMFilesOut = new File(subjectDirectory, String.format("%s.merged.bam", subjectName));
            files2RegisterToIRODS.add(new IRODSBean(mergeBAMFilesOut, attributeListWithJob));

            attributeListWithJob = new ArrayList<>(attributeList);
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqJobName", SAMToolsIndex.class.getSimpleName()));
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqMimeType", MimeType.APPLICATION_BAM_INDEX.toString()));
            File mergeBAMFilesIndexOut = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".bai"));
            files2RegisterToIRODS.add(new IRODSBean(mergeBAMFilesIndexOut, attributeListWithJob));

            attributeListWithJob = new ArrayList<>(attributeList);
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqJobName", PicardCollectHsMetrics.class.getSimpleName()));
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqMimeType", MimeType.TEXT_PLAIN.toString()));
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqReferenceSequenceFile", referenceSequence));
            File picardCollectHsMetricsFile = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".hs.metrics"));
            files2RegisterToIRODS.add(new IRODSBean(picardCollectHsMetricsFile, attributeListWithJob));

            attributeListWithJob = new ArrayList<>(attributeList);
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqJobName", FreeBayes.class.getSimpleName()));
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqMimeType", MimeType.TEXT_VCF.toString()));
            attributeListWithJob.add(new ImmutablePair<String, String>("MaPSeqReferenceSequenceFile", referenceSequence));
            File freeBayesOutput = new File(subjectDirectory, mergeBAMFilesOut.getName().replace(".bam", ".vcf"));
            files2RegisterToIRODS.add(new IRODSBean(freeBayesOutput, attributeListWithJob));

            for (IRODSBean bean : files2RegisterToIRODS) {

                commandInput = new CommandInput();
                commandInput.setExitImmediately(Boolean.FALSE);

                File f = bean.getFile();
                if (!f.exists()) {
                    logger.warn("file to register doesn't exist: {}", f.getAbsolutePath());
                    continue;
                }

                StringBuilder registerCommandSB = new StringBuilder();
                String registrationCommand = String.format("$IRODS_HOME/ireg -f %s %s/%s", bean.getFile().getAbsolutePath(), irodsDirectory,
                        bean.getFile().getName());
                String deRegistrationCommand = String.format("$IRODS_HOME/irm -U %s/%s", irodsDirectory, bean.getFile().getName());
                registerCommandSB.append(registrationCommand).append("\n");
                registerCommandSB.append(String.format("if [ $? != 0 ]; then %s; %s; fi%n", deRegistrationCommand, registrationCommand));
                commandInput.setCommand(registerCommandSB.toString());
                commandInput.setWorkDir(tmpDir);
                commandInputList.add(commandInput);

                commandInput = new CommandInput();
                commandInput.setExitImmediately(Boolean.FALSE);
                sb = new StringBuilder();
                for (ImmutablePair<String, String> attribute : bean.getAttributes()) {
                    sb.append(String.format("$IRODS_HOME/imeta add -d %s/%s %s %s GeneScreen%n", irodsDirectory, bean.getFile().getName(),
                            attribute.getLeft(), attribute.getRight()));
                }
                commandInput.setCommand(sb.toString());
                commandInput.setWorkDir(tmpDir);
                commandInputList.add(commandInput);

            }

            File mapseqrc = new File(System.getProperty("user.home"), ".mapseqrc");
            Executor executor = BashExecutor.getInstance();

            for (CommandInput ci : commandInputList) {
                try {
                    logger.debug("ci.getCommand(): {}", ci.getCommand());
                    commandOutput = executor.execute(ci, mapseqrc);
                    if (commandOutput.getExitCode() != 0) {
                        logger.info("commandOutput.getExitCode(): {}", commandOutput.getExitCode());
                        logger.warn("command failed: {}", ci.getCommand());
                    }
                    logger.debug("commandOutput.getStdout(): {}", commandOutput.getStdout());
                } catch (ExecutorException e) {
                    if (commandOutput != null) {
                        logger.warn("commandOutput.getStderr(): {}", commandOutput.getStderr());
                    }
                }
            }

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    public MaPSeqDAOBeanService getMapseqDAOBeanService() {
        return mapseqDAOBeanService;
    }

    public void setMapseqDAOBeanService(MaPSeqDAOBeanService mapseqDAOBeanService) {
        this.mapseqDAOBeanService = mapseqDAOBeanService;
    }

    public String getSubjectMergeHome() {
        return subjectMergeHome;
    }

    public void setSubjectMergeHome(String subjectMergeHome) {
        this.subjectMergeHome = subjectMergeHome;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

}
