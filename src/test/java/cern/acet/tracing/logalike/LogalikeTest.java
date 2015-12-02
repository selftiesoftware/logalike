/**
 * Logalike - A stream based message processor
 * Copyright (c) 2015 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 * This software is distributed under the terms of the GNU General Public Licence version 3 (GPL Version 3),
 * copied verbatim in the file “COPYLEFT”.
 * In applying this licence, CERN does not waive the privileges and immunities granted to it by virtue
 * of its status as an Intergovernmental Organization or submit itself to any jurisdiction. 
 * 
 * Authors: Gergő Horányi <ghoranyi> and Jens Egholm Pedersen <jegp>
 */

package cern.acet.tracing.logalike;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.Before;
import org.junit.Test;

import cern.acet.tracing.Input;
import cern.acet.tracing.Logalike;
import cern.acet.tracing.LogalikeImpl;
import cern.acet.tracing.MessageImpl;
import cern.acet.tracing.Output;
import cern.acet.tracing.processing.Processor;
import cern.acet.tracing.util.type.strategy.DropStrategy;

public class LogalikeTest {

    private LogalikeImpl<MessageImpl> logalike;
    private MessageImpl message;
    private Input<MessageImpl> mockInput;
    private Output<MessageImpl> mockOutput;
    private Processor<MessageImpl> processor;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        processor = stream -> stream;
        mockInput = mock(Input.class);
        mockOutput = mock(Output.class);
        message = new MessageImpl();
    }

    @Test
    public void canProcessMessages() {
        runFinite(Stream.of(message));
        verify(mockOutput).accept(message);
    }

    @Test
    public void canProcessInfiniteStream() throws Exception {
        runInfinite(Stream.generate(() -> message));
        verify(mockOutput, atLeast(2)).accept(message);
    }

    @Test
    public void canProcessConcatenatedStream() throws Exception {
        MessageImpl message2 = new MessageImpl(DropStrategy.INSTANCE);
        Stream<MessageImpl> concatenated = Stream.concat(streamGenerator(message), streamGenerator(message2));
        runInfinite(concatenated);
        verify(mockOutput, atLeastOnce()).accept(message);
        verify(mockOutput, atLeastOnce()).accept(message2);
    }

    @Test
    public void canCloseInfiniteStream() throws Exception {
        AtomicBoolean isClosed = new AtomicBoolean(false);
        runInfinite(Stream.generate(() -> message).onClose(() -> isClosed.set(true)));
        assertTrue(isClosed.get());
    }

    @Test
    public void canFilterMessages() {
        AtomicBoolean shouldAllow = new AtomicBoolean(true);
        processor = s -> s.filter(m -> shouldAllow.getAndSet(false));
        runFinite(Stream.of(message, message, message, message));
        verify(mockOutput).accept(message);
    }

    @Test
    public void canRunFiniteStream() {
        setInput(Stream.of(new MessageImpl()));
        logalike.run();
    }

    private void setInput(Stream<MessageImpl> stream) {
        when(mockInput.get()).thenReturn(stream);
        logalike = Logalike.<MessageImpl> builder().setInput(mockInput).setOutput(mockOutput)
                .addProcessor(processor).build();
    }

    private void runFinite(Stream<MessageImpl> stream) {
        setInput(stream.parallel());
        logalike.run();
    }

    private <T> void runInfinite(Stream<MessageImpl> stream) throws Exception {
        setInput(stream);
        Thread t = new Thread(logalike);
        t.start();
        t.join(200);
        logalike.close();
        t.join();
    }

    private Stream<MessageImpl> streamGenerator(MessageImpl messageToGenerate) {
        return Stream.generate(() -> messageToGenerate);
    }

}