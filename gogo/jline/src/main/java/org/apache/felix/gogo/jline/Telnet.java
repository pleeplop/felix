/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.gogo.jline;

import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Process;
import org.jline.builtins.Options;
import org.jline.builtins.telnet.Connection;
import org.jline.builtins.telnet.ConnectionData;
import org.jline.builtins.telnet.ConnectionEvent;
import org.jline.builtins.telnet.ConnectionListener;
import org.jline.builtins.telnet.ConnectionManager;
import org.jline.builtins.telnet.PortListener;
import org.jline.builtins.telnet.TelnetIO;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;

/*
 * a very simple Telnet server.
 * real remote access should be via ssh.
 */
public class Telnet
{
    static final String[] functions = { "telnetd" };

    private static final int defaultPort = 2019;
    private final CommandProcessor processor;
    private PortListener portListener;
    private int port;
    private String ip;

    public Telnet(CommandProcessor procesor)
    {
        this.processor = procesor;
    }

    public void _main(CommandSession session, String[] argv) throws IOException
    {
        if (argv == null || argv.length < 1) {
            throw new IllegalArgumentException();
        }

        Process process = Process.Utils.current();
        try {
            run(session, process, argv);
        } catch (IllegalArgumentException e) {
            process.err().println(e.getMessage());
            process.error(2);
        } catch (Posix.HelpException e) {
            process.err().println(e.getMessage());
            process.error(0);
        } catch (Exception e) {
            process.err().println(argv[0] + ": " + e.toString());
            process.error(1);
        }


    }

    protected Object run(CommandSession session, Process process, String[] argv) throws Exception
    {
        switch (argv[0])
        {
            case "telnetd":
                telnetd(session, process, argv);
                break;
        }
        return null;
    }

    protected void telnetd(CommandSession session, Process process, String[] argv) throws Exception
    {
        final String[] usage = {"telnetd - start simple telnet server",
                "Usage: telnetd [-i ip] [-p port] start | stop | status",
                "  -i --ip=INTERFACE        listen interface (default=127.0.0.1)",
                "  -p --port=PORT           listen port (default=" + defaultPort + ")",
                "  -? --help                show help"};

        Options opt = parseOptions(session, usage, argv);
        List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty())
        {
            opt.usage(System.err);
            return;
        }

        String command = args.get(0);

        if ("start".equals(command))
        {
            if (portListener != null)
            {
                throw new IllegalStateException("telnetd is already running on port " + port);
            }
            ip = opt.get("ip");
            port = opt.getNumber("port");
            start(session);
            status(process);
        }
        else if ("stop".equals(command))
        {
            if (portListener == null) {
                throw new IllegalStateException("telnetd is not running.");
            }
            stop();
        }
        else if ("status".equals(command))
        {
            status(process);
        }
        else
        {
            throw opt.usageError("bad command: " + command);
        }
    }

    protected Options parseOptions(CommandSession session, String[] usage, Object[] argv) throws Exception
    {
        Options opt = Options.compile(usage, s -> get(session, s)).parse(argv, true);
        if (opt.isSet("help"))
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            opt.usage(new PrintStream(baos));
            throw new Posix.HelpException(baos.toString());
        }
        return opt;
    }

    protected String get(CommandSession session, String name)
    {
        Object o = session.get(name);
        return o != null ? o.toString() : null;
    }

    private void status(Process process)
    {
        if (portListener != null)
        {
            process.out().println("telnetd is running on " + ip + ":" + port);
        }
        else
        {
            process.out().println("telnetd is not running.");
        }
    }

    private void start(CommandSession session)
    {
        ConnectionManager connectionManager = new ConnectionManager(1000, 5 * 60 * 1000, 5 * 60 * 1000, 60 * 1000, null, null, false)
        {
            @Override
            protected Connection createConnection(ThreadGroup threadGroup, ConnectionData newCD)
            {
                return new Connection(threadGroup, newCD)
                {
                    TelnetIO telnetIO;

                    @Override
                    protected void doRun() throws Exception
                    {
                        telnetIO = new TelnetIO();
                        telnetIO.setConnection(this);
                        telnetIO.initIO();

                        InputStream in = new InputStream()
                        {
                            @Override
                            public int read() throws IOException
                            {
                                return telnetIO.read();
                            }
                            @Override
                            public int read(byte[] b, int off, int len) throws IOException
                            {
                                int r = read();
                                if (r >= 0) {
                                    b[off] = (byte) r;
                                    return 1;
                                }
                                else
                                {
                                    return -1;
                                }
                            }
                        };
                        PrintStream out = new PrintStream(new OutputStream()
                        {
                            @Override
                            public void write(int b) throws IOException
                            {
                                telnetIO.write(b);
                            }
                            @Override
                            public void flush() throws IOException
                            {
                                telnetIO.flush();
                            }
                        });
                        Terminal terminal = TerminalBuilder.builder()
                                .type(getConnectionData().getNegotiatedTerminalType().toLowerCase())
                                .streams(in, out)
                                .system(false)
                                .name("telnet")
                                .build();
                        terminal.setSize(new Size(getConnectionData().getTerminalColumns(), getConnectionData().getTerminalRows()));
                        terminal.setAttributes(Shell.getTerminal(session).getAttributes());
                        addConnectionListener(new ConnectionListener() {
                            @Override
                            public void connectionIdle(ConnectionEvent ce)
                            {
                            }

                            @Override
                            public void connectionTimedOut(ConnectionEvent ce)
                            {
                            }

                            @Override
                            public void connectionLogoutRequest(ConnectionEvent ce)
                            {
                            }

                            @Override
                            public void connectionSentBreak(ConnectionEvent ce)
                            {
                            }

                            @Override
                            public void connectionTerminalGeometryChanged(ConnectionEvent ce)
                            {
                                terminal.setSize(new Size(getConnectionData().getTerminalColumns(), getConnectionData().getTerminalRows()));
                                terminal.raise(Terminal.Signal.WINCH);
                            }
                        });
                        PrintStream pout = new PrintStream(terminal.output());
                        CommandSession session = processor.createSession(terminal.input(), pout, pout);
                        session.put(Shell.VAR_TERMINAL, terminal);
                        Shell.Context context = new Shell.Context()
                        {
                            @Override
                            public String getProperty(String name)
                            {
                                return System.getProperty(name);
                            }
                            @Override
                            public void exit()
                            {
                                close();
                            }
                        };
                        new Shell(context, processor).gosh(session, new String[]{"--login"});
                    }

                    @Override
                    protected void doClose()
                    {
                        telnetIO.closeOutput();
                        telnetIO.closeInput();
                    }
                };
            }
        };
        portListener = new PortListener("gogo", port, 10);
        portListener.setConnectionManager(connectionManager);
        portListener.start();
    }

    private void stop() throws IOException
    {
        portListener.stop();
        portListener = null;
    }
}
