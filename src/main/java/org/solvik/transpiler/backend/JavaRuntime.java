package org.solvik.transpiler.backend;

import java.util.EnumSet;

/**
 * Emits the self-contained {@code RT} runtime as a set of cohesive feature
 * modules.
 *
 * <p>Only the modules selected by {@link RuntimeFeature} reachability are
 * emitted, so a program that never uses, say, regex or processes does not carry
 * that code. The generated Java still has no dependency outside the JDK; this
 * class only decides which internal helper block to write.</p>
 */
public final class JavaRuntime {

    /** Receives generated lines and controls indentation within the emitter. */
    public interface RuntimeSink {
        void line(String text);
        void indent();
        void outdent();
    }

    public void emit(RuntimeSink sink, EnumSet<RuntimeFeature> features) {
        sink.line("private static final class RT {"); sink.indent();
        sink.line("private RT() {}");
        emitRuntimeBase(sink);
        if (features.contains(RuntimeFeature.LIST)) emitListRuntime(sink);
        if (features.contains(RuntimeFeature.MAP)) emitMapRuntime(sink);
        if (features.contains(RuntimeFeature.STACK)) emitStackRuntime(sink);
        if (features.contains(RuntimeFeature.SET)) emitSetRuntime(sink);
        if (features.contains(RuntimeFeature.ARITHMETIC)) emitArithmeticRuntime(sink);
        if (features.contains(RuntimeFeature.REGEX)) emitRegexRuntime(sink);
        if (features.contains(RuntimeFeature.THREAD)) emitThreadRuntime(sink);
        if (features.contains(RuntimeFeature.MUTEX)) emitMutexRuntime(sink);
        if (features.contains(RuntimeFeature.SEMAPHORE)) emitSemaphoreRuntime(sink);
        if (features.contains(RuntimeFeature.PROCESS)) emitProcessRuntime(sink);
        if (features.contains(RuntimeFeature.JSON)) emitJsonRuntime(sink);
        if (features.contains(RuntimeFeature.HASH)) emitHashRuntime(sink);
        if (features.contains(RuntimeFeature.FILE)) emitFileRuntime(sink);
        if (features.contains(RuntimeFeature.DYNAMIC)) emitDynamicRuntime(sink);
        if (features.contains(RuntimeFeature.RANDOM)) emitRandomRuntime(sink);
        if (features.contains(RuntimeFeature.PROPERTIES)) emitPropertiesRuntime(sink);
        if (features.contains(RuntimeFeature.ENV)) emitEnvRuntime(sink);
        if (features.contains(RuntimeFeature.TYPE)) emitTypeRuntime(sink);
        if (features.contains(RuntimeFeature.CONVERSIONS)) emitConversionsRuntime(sink);
        if (features.contains(RuntimeFeature.STRING_ITER)) emitCodePointsRuntime(sink);
        if (features.contains(RuntimeFeature.STRING_ACCESS)) emitStringAccessRuntime(sink);
        if (features.contains(RuntimeFeature.RANGE)) emitRangeRuntime(sink);
        if (features.contains(RuntimeFeature.TIME)) emitTimeRuntime(sink);
        if (features.contains(RuntimeFeature.TEST)) emitTestRuntime(sink);
        sink.outdent(); sink.line("}");
    }

    private void emitRuntimeBase(RuntimeSink sink) {
        sink.line("interface RuntimeValue {}");
        sink.line("static boolean always(){return System.nanoTime()!=Long.MIN_VALUE;}");
        sink.line("static String formatDouble(double x) { return Double.isFinite(x) ? BigDecimal.valueOf(x).stripTrailingZeros().toPlainString() : Double.toString(x); }");
        sink.line("static String formatFloat(float x) { return Float.isFinite(x) ? new BigDecimal(Float.toString(x)).stripTrailingZeros().toPlainString() : Float.toString(x); }");
        sink.line("static String format(Object x) { if(x==null)return \"null\"; if(x instanceof Double d && Double.isFinite(d)) return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString(); if(x instanceof Float f && Float.isFinite(f)) return new BigDecimal(Float.toString(f)).stripTrailingZeros().toPlainString(); return x.toString(); }");
        sink.line("static String collectionValue(Object x){if(x==null)return \"null\";if(x instanceof String||x instanceof BigInteger||x instanceof BigDecimal||x instanceof Iterable||x instanceof RuntimeValue||x.getClass().getSimpleName().startsWith(\"__S_\"))return \"<object>\";return format(x);}");
        sink.line("static String cat(Object a, Object b) { return format(a) + format(b); }");
        sink.line("static <T> T require(T x) { if(x==null) throw new Thrown(new ExceptionValue(\"null reference\")); return x; }");
        sink.line("static int compare(Object a,Object b) { if(a instanceof String x && b instanceof String y) return x.compareTo(y); if(a instanceof Number x && b instanceof Number y) return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())); throw new IllegalArgumentException(\"values are not comparable\"); }");
        sink.line("static void rejectMutableKey(Object x,String operation){if(x instanceof Iterable)throw new Thrown(new ExceptionValue(operation+\": mutable values cannot be used as Map keys or Set members\"));} @SuppressWarnings(\"unchecked\") static <T> T canonicalKey(T x){if(x instanceof BigDecimal d)return (T)d.stripTrailingZeros();if(x instanceof BigInteger i)return (T)i;return x;} static int hash(Object x){if(x instanceof BigDecimal d)return d.stripTrailingZeros().hashCode();if(x instanceof BigInteger i)return i.hashCode();return Objects.hashCode(x);}");
        sink.line("static <T> T noMatch() { throw new Thrown(new ExceptionValue(\"non-exhaustive match\")); }");
        sink.line("static boolean eq(Object a, Object b) { if (a instanceof Number x && b instanceof Number y) { double dx=x.doubleValue(), dy=y.doubleValue(); if (Double.isNaN(dx)||Double.isNaN(dy)) return false; if (Double.isInfinite(dx)||Double.isInfinite(dy)) return dx==dy; return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0; } if (a == b) return true; if (a == null || b == null) return false; return a.equals(b); }");
        sink.line("static final class Writer { private PrintStream stream; Writer(PrintStream s){stream=s;} Writer(OutputStream s){this(new PrintStream(s,true,StandardCharsets.UTF_8));} synchronized void print(Object x){stream.print(format(x));} synchronized void println(Object x){stream.println(format(x));} synchronized void print(long x){stream.print(x);} synchronized void println(long x){stream.println(x);} synchronized void print(int x){stream.print(x);} synchronized void println(int x){stream.println(x);} synchronized void print(short x){stream.print(x);} synchronized void println(short x){stream.println(x);} synchronized void print(byte x){stream.print(x);} synchronized void println(byte x){stream.println(x);} synchronized void print(boolean x){stream.print(x);} synchronized void println(boolean x){stream.println(x);} synchronized void print(float x){stream.print(formatFloat(x));} synchronized void println(float x){stream.println(formatFloat(x));} synchronized void print(double x){stream.print(formatDouble(x));} synchronized void println(double x){stream.println(formatDouble(x));} synchronized void redirect(Writer w){stream=w.stream;} }");
        sink.line("static final class Reader { private final InputStream input; private final byte[] chunk = new byte[8192]; private int chunkStart, chunkEnd; Reader(InputStream s){input=new BufferedInputStream(s);} String readln(){try{ByteArrayOutputStream b=new ByteArrayOutputStream();boolean any=false;while(true){if(chunkStart>=chunkEnd){int n=input.read(chunk);if(n<0)break;chunkStart=0;chunkEnd=n;}int x=chunk[chunkStart++]&0xFF;any=true;if(x=='\\n')break;if(x!='\\r')b.write(x);}if(!any)return null;return b.toString(StandardCharsets.UTF_8);}catch(IOException e){throw new RuntimeException(e);}} String readAll(){try{ByteArrayOutputStream b=new ByteArrayOutputStream();while(chunkStart<chunkEnd)b.write(chunk[chunkStart++]);byte[] rest=input.readAllBytes();b.write(rest,0,rest.length);return b.toString(StandardCharsets.UTF_8);}catch(IOException e){throw new RuntimeException(e);}} }");
        sink.line("static final class ExceptionValue implements RuntimeValue { final String message; ExceptionValue(String m){message=m;} @Override public String toString(){return message;} }");
        sink.line("@SuppressWarnings(\"serial\") static final class Thrown extends RuntimeException { final Object value; Thrown(Object v){value=v;} }");
        sink.line("static final Writer OUT=new Writer(System.out); static final Writer ERR=new Writer(System.err); static final Reader IN=new Reader(System.in); static Writer out(){return OUT;} static Writer err(){return ERR;} static Reader in(){return IN;}");
    }

    private void emitArithmeticRuntime(RuntimeSink sink) {
        sink.line("static int divInt(int a, int b) { if (b == 0) throw new ArithmeticException(\"division by zero\"); if (a == Integer.MIN_VALUE && b == -1) throw new ArithmeticException(\"integer overflow\"); return a / b; }");
        sink.line("static long divLong(long a, long b) { if (b == 0) throw new ArithmeticException(\"division by zero\"); if (a == Long.MIN_VALUE && b == -1) throw new ArithmeticException(\"integer overflow\"); return a / b; }");
        sink.line("static int remInt(int a,int b){if(b==0)throw new ArithmeticException(\"division by zero\");if(a==Integer.MIN_VALUE&&b==-1)throw new ArithmeticException(\"integer overflow\");return a%b;} static long remLong(long a,long b){if(b==0)throw new ArithmeticException(\"division by zero\");if(a==Long.MIN_VALUE&&b==-1)throw new ArithmeticException(\"integer overflow\");return a%b;} static int absInt(int a){return a==Integer.MIN_VALUE?throwIntOverflow():Math.abs(a);} static long absLong(long a){return a==Long.MIN_VALUE?throwLongOverflow():Math.abs(a);} static int throwIntOverflow(){throw new ArithmeticException(\"integer overflow\");} static long throwLongOverflow(){throw new ArithmeticException(\"integer overflow\");}");
    }

    private void emitRegexRuntime(RuntimeSink sink) {
        sink.line("static final ConcurrentMap<String,Pattern> REGEXES = new ConcurrentHashMap<>(); static Pattern regex(String s){Pattern p=REGEXES.get(s);if(p!=null)return p;p=Pattern.compile(s);if(REGEXES.size()<1024)REGEXES.putIfAbsent(s,p);return p;} static final class Regex implements RuntimeValue { final Pattern p; Regex(String s){p=regex(s);} boolean matches(String s){return p.matcher(s).find();} String find(String s){Matcher m=p.matcher(s);return m.find()?m.group():null;} SList<String> all(String s){SList<String> r=new SList<>(); Matcher m=p.matcher(s); while(m.find())r.add(m.group()); return r;} String replace(String s,String x){return p.matcher(s).replaceAll(x);} }");
    }

    private void emitThreadRuntime(RuntimeSink sink) {
        sink.line("interface RunnableLike { void run(); }");
        sink.line("static final class ThreadValue implements RuntimeValue { final Thread t; ThreadValue(RunnableLike r){t=new Thread(r::run);} void start(){t.start();} void join(){try{t.join();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}} }");
    }

    private void emitMutexRuntime(RuntimeSink sink) {
        sink.line("static final class MutexValue implements RuntimeValue { final ReentrantLock lock=new ReentrantLock(); void lock(){lock.lock();} void unlock(){lock.unlock();} }");
    }

    private void emitSemaphoreRuntime(RuntimeSink sink) {
        sink.line("static final class SemaphoreValue implements RuntimeValue { final Semaphore sem; SemaphoreValue(long n){sem=new Semaphore(Math.toIntExact(n));} void acquire(){try{sem.acquire();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}} void release(){sem.release();} }");
    }

    private void emitProcessRuntime(RuntimeSink sink) {
        sink.line("static final class ProcessValue implements RuntimeValue { Process p; final String command; final SList<String> args; Reader outReader,errReader; Writer inWriter; volatile ByteArrayOutputStream outBuffer,errBuffer; Thread outDrainer,errDrainer; boolean outClaimed,errClaimed; ProcessValue(String c,SList<String>a){command=c;args=a;} void start(){try{List<String> commandLine=new ArrayList<>();if(args.data.isEmpty()&&command.indexOf(' ')>=0){commandLine.add(\"/bin/sh\");commandLine.add(\"-c\");commandLine.add(command);}else{commandLine.add(command);commandLine.addAll(args.data);}p=new ProcessBuilder(commandLine).start();}catch(IOException e){throw new RuntimeException(e);}} void drain(boolean output){try{InputStream in=output?p.getInputStream():p.getErrorStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;while((n=in.read(chunk))>=0)buffer.write(chunk,0,n);if(output)outBuffer=buffer;else errBuffer=buffer;}catch(IOException e){throw new RuntimeException(e);}} synchronized void beginDrain(boolean output){if(output&&!outClaimed&&outDrainer==null){outDrainer=new Thread(()->drain(true));outDrainer.start();}if(!output&&!errClaimed&&errDrainer==null){errDrainer=new Thread(()->drain(false));errDrainer.start();}} void join(Thread t){try{if(t!=null)t.join();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}} void waitFor(){beginDrain(true);beginDrain(false);try{p.waitFor();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}join(outDrainer);join(errDrainer);} long exitCode(){return p.exitValue();} synchronized Reader stdout(){if(outReader==null){if(outDrainer!=null){join(outDrainer);outReader=new Reader(new ByteArrayInputStream(outBuffer.toByteArray()));}else{outClaimed=true;outReader=new Reader(p.getInputStream());}}return outReader;} synchronized Reader stderr(){if(errReader==null){if(errDrainer!=null){join(errDrainer);errReader=new Reader(new ByteArrayInputStream(errBuffer.toByteArray()));}else{errClaimed=true;errReader=new Reader(p.getErrorStream());}}return errReader;} synchronized Writer stdin(){if(inWriter==null)inWriter=new Writer(p.getOutputStream());return inWriter;} }");
    }

    private void emitJsonRuntime(RuntimeSink sink) {
        sink.line("static String json(Object x){return jsonValue(x,new IdentityHashMap<>());} static String jsonQuote(String s){StringBuilder b=new StringBuilder(\"\\\"\");for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case '\\\\'->b.append(\"\\\\\\\\\");case '\\\"'->b.append(\"\\\\\\\"\");case '\\b'->b.append(\"\\\\b\");case '\\f'->b.append(\"\\\\f\");case '\\n'->b.append(\"\\\\n\");case '\\r'->b.append(\"\\\\r\");case '\\t'->b.append(\"\\\\t\");default-> {if(c<0x20)b.append(String.format(\"\\\\u%04x\",(int)c));else b.append(c);}}}return b.append('\\\"').toString();} static String jsonValue(Object x,IdentityHashMap<Object,Boolean> seen){if(x==null)return \"null\";if(x instanceof String s)return jsonQuote(s);if(x instanceof Boolean||x instanceof Number)return format(x);if(x instanceof SList<?> l){if(seen.put(l,Boolean.TRUE)!=null)throw new Thrown(new ExceptionValue(\"cyclic value is not JSON-serializable\"));StringJoiner j=new StringJoiner(\",\",\"[\",\"]\");for(Object v:l.snapshot())j.add(jsonValue(v,seen));seen.remove(l);return j.toString();}if(x instanceof SMap<?,?> m){if(seen.put(m,Boolean.TRUE)!=null)throw new Thrown(new ExceptionValue(\"cyclic value is not JSON-serializable\"));StringJoiner j=new StringJoiner(\",\",\"{\",\"}\");for(Map.Entry<?,?> e:m.snapshot().entrySet())j.add(jsonQuote(format(e.getKey()))+\":\"+jsonValue(e.getValue(),seen));seen.remove(m);return j.toString();}return jsonQuote(format(x));} static Object parseJson(String x){return x;}");
    }

    private void emitHashRuntime(RuntimeSink sink) {
        sink.line("static final char[] HEX = \"0123456789abcdef\".toCharArray();");
        sink.line("static String digest(String algorithm,String s){try{byte[] b=MessageDigest.getInstance(algorithm).digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder r=new StringBuilder(b.length*2);for(byte x:b){r.append(HEX[(x>>4)&0xF]);r.append(HEX[x&0xF]);}return r.toString();}catch(Exception e){throw new RuntimeException(e);}}");
    }

    private void emitFileRuntime(RuntimeSink sink) {
        sink.line("static String fileRead(String p){try{return Files.readString(Path.of(p));}catch(IOException e){throw new RuntimeException(e);}} static void fileWrite(String p,String s){try{Files.writeString(Path.of(p),s);}catch(IOException e){throw new RuntimeException(e);}} static boolean fileExists(String p){return Files.exists(Path.of(p));} static boolean fileDelete(String p){try{return Files.deleteIfExists(Path.of(p));}catch(IOException e){throw new RuntimeException(e);}} static SList<String> fileList(String p){try{SList<String> r=new SList<>();try(var ds=Files.newDirectoryStream(Path.of(p))){for(Path x:ds)r.add(x.getFileName().toString());}return r;}catch(IOException e){throw new RuntimeException(e);}}");
    }

    private void emitDynamicRuntime(RuntimeSink sink) {
        sink.line("static Object dynamic(Object target,String name,Object... args) { if(target==null) throw new NullPointerException(\"dynamic call on null\"); if(!target.getClass().getSimpleName().startsWith(\"__S_\")) throw new IllegalArgumentException(\"no method '\"+name+\"' on \"+target.getClass().getSimpleName()); for(java.lang.reflect.Method m:target.getClass().getMethods()) if(m.getName().equals(name)&&m.getParameterCount()==args.length) try { Object[] converted=new Object[args.length]; Class<?>[] types=m.getParameterTypes(); for(int i=0;i<args.length;i++) converted[i]=adapt(args[i],types[i]); m.setAccessible(true); return m.invoke(target,converted); } catch(java.lang.reflect.InvocationTargetException e) { if(e.getCause() instanceof RuntimeException r) throw r; if(e.getCause() instanceof Error x) throw x; throw new RuntimeException(e.getCause()); } catch(ReflectiveOperationException | IllegalArgumentException e) { } throw new IllegalArgumentException(\"unknown dynamic member \"+name); }");
        sink.line("static Object adapt(Object value,Class<?> type) { if(value==null||type.isInstance(value)) return value; if(value instanceof Number n) { if(type==Byte.class||type==byte.class)return n.byteValue(); if(type==Short.class||type==short.class)return n.shortValue(); if(type==Integer.class||type==int.class)return n.intValue(); if(type==Long.class||type==long.class)return n.longValue(); if(type==Float.class||type==float.class)return n.floatValue(); if(type==Double.class||type==double.class)return n.doubleValue(); } return value; }");
    }

    private void emitRandomRuntime(RuntimeSink sink) {
        sink.line("static long randomState; static synchronized void seedRandom(long seed){randomState=seed==0?0x9e3779b97f4a7c15L:seed;} static synchronized long randomBits(){randomState^=randomState<<13;randomState^=randomState>>>7;randomState^=randomState<<17;return randomState;} static synchronized long randomLong(long bound){if(bound<=0)throw new IllegalArgumentException(\"random bound must be positive\");return Long.remainderUnsigned(randomBits(),bound)+1;} static synchronized double randomDouble(){return (randomBits()>>>11)*0x1.0p-53;}");
    }

    private void emitPropertiesRuntime(RuntimeSink sink) {
        sink.line("static final ConcurrentMap<String,String> PROPS = new ConcurrentHashMap<>();");
        sink.line("static String property(String k){return PROPS.get(k);} static String property(String k,String d){return PROPS.getOrDefault(k,d);} static String setProperty(String k,String v){return PROPS.put(k,v);} static String clearProperty(String k){return PROPS.remove(k);}");
    }

    private void emitEnvRuntime(RuntimeSink sink) {
        sink.line("static String getenv(String k){return System.getenv(k);} static SMap<String,String> getenv(){SMap<String,String> r=new SMap<>();r.data.putAll(System.getenv());return r;} ");
    }

    private void emitTypeRuntime(RuntimeSink sink) {
        sink.line("static String typeOf(Object x) { if(x==null)return \"null\"; if(x instanceof Byte)return \"Byte\"; if(x instanceof Short)return \"Short\"; if(x instanceof Integer)return \"Integer\"; if(x instanceof Long)return \"Long\"; if(x instanceof Float)return \"Float\"; if(x instanceof Double)return \"Double\"; if(x instanceof String)return \"String\"; if(x instanceof Boolean)return \"Boolean\"; return x.getClass().getSimpleName(); }");
        sink.line("static boolean isType(Object x, String n) { return typeOf(x).equals(n); }");
    }

    private void emitConversionsRuntime(RuntimeSink sink) {
        sink.line("static String convertString(Object x) { return format(x); }");
        sink.line("static long convertLong(Object x) { if(x instanceof BigInteger b)return b.longValueExact(); if(x instanceof BigDecimal d){if(d.compareTo(BigDecimal.valueOf(Long.MIN_VALUE))<0||d.compareTo(BigDecimal.valueOf(Long.MAX_VALUE))>0)throw new ArithmeticException(\"value out of Long range\");return d.longValue();} if(x instanceof Double d){if(!Double.isFinite(d)||d<=-0x1.0p63||d>=0x1.0p63)throw new ArithmeticException(\"value out of Long range\");return d.longValue();} if(x instanceof Float f){if(!Float.isFinite(f)||f<=-0x1.0p63f||f>=0x1.0p63f)throw new ArithmeticException(\"value out of Long range\");return f.longValue();} if(x instanceof Number n)return n.longValue(); return Long.parseLong(String.valueOf(x)); }");
        sink.line("static int convertInt(Object x) { long n=convertLong(x); return Math.toIntExact(n); }");
        sink.line("static short convertShort(Object x) { long n=convertLong(x); if(n < Short.MIN_VALUE || n > Short.MAX_VALUE) throw new ArithmeticException(\"value out of Short range\"); return (short)n; }");
        sink.line("static byte convertByte(Object x) { long n=convertLong(x); if(n < Byte.MIN_VALUE || n > Byte.MAX_VALUE) throw new ArithmeticException(\"value out of Byte range\"); return (byte)n; }");
        sink.line("static double convertDouble(Object x) { if (x instanceof Number n) return n.doubleValue(); return Double.parseDouble(String.valueOf(x)); }");
        sink.line("static float convertFloat(Object x) { return (float)convertDouble(x); }");
        sink.line("static boolean convertBoolean(Object x) { return x instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(x)); }");
        sink.line("static String convertChar(Object x) { if(x instanceof String s) return strCharAt(s,0); long n=convertLong(x); if(n<0||n>0x10ffffL) throw new ArithmeticException(\"not a valid code point\"); return new String(Character.toChars((int)n)); }");
        sink.line("static BigInteger convertBigInteger(Object x) { return x instanceof BigInteger b ? b : new BigInteger(String.valueOf(x)); }");
        sink.line("static BigDecimal convertBigDecimal(Object x) { return x instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(x)); }");
    }

    private void emitCodePointsRuntime(RuntimeSink sink) {
        sink.line("static Iterable<String> codePoints(String s) { return () -> new Iterator<String>() { private int i; public boolean hasNext() { return i < s.length(); } public String next() { int cp = s.codePointAt(i); i += Character.charCount(cp); return new String(Character.toChars(cp)); } }; }");
    }

    private void emitStringAccessRuntime(RuntimeSink sink) {
        sink.line("static String strSubstring(String s, long a, long b) { int x = Math.toIntExact(a), y = Math.toIntExact(b); if (x < 0 || y < x || y > s.codePointCount(0, s.length())) throw new IndexOutOfBoundsException(\"string index out of range\"); return s.substring(s.offsetByCodePoints(0, x), s.offsetByCodePoints(0, y)); }");
        sink.line("static String strCharAt(String s, long i) { int x = Math.toIntExact(i); if (x < 0 || x >= s.codePointCount(0, s.length())) throw new IndexOutOfBoundsException(\"char index out of range\"); return new String(Character.toChars(s.codePointAt(s.offsetByCodePoints(0, x)))); }");;
    }

    private void emitRangeRuntime(RuntimeSink sink) {
        sink.line("static SList<Long> range(long start, long end, boolean inclusive) { SList<Long> r=new SList<>(); long limit=inclusive?Math.addExact(end,1L):end; for(long i=start;i<limit;i=Math.addExact(i,1L)) r.add(i); return r; }");
    }

    private void emitTimeRuntime(RuntimeSink sink) {
        sink.line("static void sleep(long n){try{Thread.sleep(n);}catch(InterruptedException e){Thread.currentThread().interrupt();}}");
    }

    private void emitTestRuntime(RuntimeSink sink) {
        sink.line("static void assertValue(boolean x,Object... message){if(!x)throw new Thrown(new ExceptionValue(\"assertion failed: \"+(message.length==0?\"assertion\":format(message[0]))));} static void assertEqual(Object a,Object b,Object... message){if(!eq(a,b))throw new Thrown(new ExceptionValue(\"assertion failed: expected \"+format(a)+\" equal to \"+format(b)));}");
    }

    private void emitListRuntime(RuntimeSink sink) {
        sink.line("@SuppressWarnings(\"unchecked\") static <T> SList<T> list(Object... values) { SList<T> r = new SList<>(); for (Object v : values) r.add((T)v); return r; }");
        sink.line("static SList<String> strings(String[] values) { SList<String> r = new SList<>(); Collections.addAll(r.data, values); return r; }");
        sink.line("static int compareValues(Object a, Object b) { if (a instanceof Number na && b instanceof Number nb) { boolean bigA = na instanceof BigInteger || na instanceof BigDecimal; boolean bigB = nb instanceof BigInteger || nb instanceof BigDecimal; if (bigA && bigB) { if (na instanceof BigInteger ba && nb instanceof BigInteger bb) return ba.compareTo(bb); if (na instanceof BigDecimal da && nb instanceof BigDecimal db) return da.compareTo(db); } if (bigA || bigB) throw new Thrown(new ExceptionValue(\"list contains incomparable elements\")); boolean floatA = na instanceof Float || na instanceof Double; boolean floatB = nb instanceof Float || nb instanceof Double; if (!floatA && !floatB) return Long.compare(na.longValue(), nb.longValue()); double x = na.doubleValue(), y = nb.doubleValue(); if (Double.isNaN(x) || Double.isNaN(y)) return 0; return Double.compare(x, y); } if (a instanceof String sa && b instanceof String sb) return sa.compareTo(sb); throw new Thrown(new ExceptionValue(\"list contains incomparable elements\")); }");
        sink.line("static final class SList<T> implements Iterable<T> { final List<T> data; SList(){data=new ArrayList<>();} SList(int capacity){data=new ArrayList<>(capacity < 0 ? 0 : capacity);} synchronized int size(){return data.size();} synchronized boolean isEmpty(){return data.isEmpty();} synchronized boolean add(T x){return data.add(x);} synchronized boolean addAll(SList<T> x){return data.addAll(x.snapshot());} synchronized List<T> snapshot(){return new ArrayList<>(data);} synchronized void addAt(int i,T x){data.add(i,x);} synchronized T get(int i){return data.get(i);} synchronized T set(int i,T x){return data.set(i,x);} synchronized T remove(int i){return data.remove(i);} synchronized boolean removeValue(T x){return data.remove(x);} synchronized boolean contains(T x){return data.contains(x);} synchronized int indexOf(T x){return data.indexOf(x);} synchronized void clear(){data.clear();} synchronized void reverse(){Collections.reverse(data);} synchronized SList<T> reversed(){SList<T> r=new SList<>();r.data.addAll(data);Collections.reverse(r.data);return r;} synchronized void sort(){data.sort(RT::compareValues);} synchronized String join(String sep){StringBuilder b=new StringBuilder();boolean f=true;for(T d:data){if(!f)b.append(sep);b.append(RT.format(d));f=false;}return b.toString();} public synchronized Iterator<T> iterator(){return new ArrayList<>(data).iterator();} @Override public synchronized String toString(){StringBuilder b=new StringBuilder(32);b.append('[');boolean f=true;for(T d:data){if(!f)b.append(\", \");b.append(RT.collectionValue(d));f=false;}return b.append(']').toString();} @Override public boolean equals(Object o){return o instanceof SList<?> l && snapshot().equals(l.snapshot());}@Override public synchronized int hashCode(){return data.hashCode();} }");
    }

    private void emitMapRuntime(RuntimeSink sink) {
        sink.line("@SuppressWarnings(\"unchecked\") static <K,V> SMap<K,V> map(Object... values) { SMap<K,V> r = new SMap<>(); for (int i=0;i+1<values.length;i+=2) r.put((K)values[i], (V)values[i+1]); return r; }");
        sink.line("static final class SMap<K,V> implements Iterable<K> { final Map<K,V> data; SMap(){data=new LinkedHashMap<>();} SMap(int capacity){data=new HashMap<>(capacity < 0 ? 0 : capacity);} synchronized int size(){return data.size();} synchronized boolean isEmpty(){return data.isEmpty();} synchronized V put(K k,V v){RT.rejectMutableKey(k,\"Map.put\");K key=RT.canonicalKey(k);return data.put(key,v);} synchronized V get(K k){return data.get(RT.canonicalKey(k));} synchronized V getOrDefault(K k,V d){return data.getOrDefault(RT.canonicalKey(k),d);} synchronized V putIfAbsent(K k,V v){RT.rejectMutableKey(k,\"Map.putIfAbsent\");K key=RT.canonicalKey(k);if(!data.containsKey(key))data.put(key,v);return null;} synchronized V replace(K k,V v){return data.replace(RT.canonicalKey(k),v);} synchronized boolean removeMapping(K k,V v){return data.remove(RT.canonicalKey(k),v);} synchronized V remove(K k){return data.remove(RT.canonicalKey(k));} synchronized boolean containsKey(K k){return data.containsKey(RT.canonicalKey(k));} synchronized boolean containsValue(V v){return data.containsValue(v);} synchronized SList<K> keys(){SList<K> r=new SList<>();r.data.addAll(data.keySet());return r;} synchronized SList<V> values(){SList<V> r=new SList<>();r.data.addAll(data.values());return r;} synchronized Map<K,V> snapshot(){return new LinkedHashMap<>(data);} synchronized void putAll(SMap<K,V> m){for(Map.Entry<K,V> e:m.snapshot().entrySet())put(e.getKey(),e.getValue());} synchronized void clear(){data.clear();} public synchronized Iterator<K> iterator(){return new ArrayList<>(data.keySet()).iterator();} @Override public synchronized String toString(){StringBuilder b=new StringBuilder(32);b.append(\"{ \");boolean f=true;for(Map.Entry<K,V> e:data.entrySet()){if(!f)b.append(\", \");b.append(RT.collectionValue(e.getKey())).append(\": \").append(RT.collectionValue(e.getValue()));f=false;}return b.append(\" }\").toString();}@Override public boolean equals(Object o){return o instanceof SMap<?,?>m && snapshot().equals(m.snapshot());}@Override public synchronized int hashCode(){return data.hashCode();} }");
    }

    private void emitStackRuntime(RuntimeSink sink) {
        sink.line("static final class SStack<T> implements Iterable<T> { final Deque<T> data; SStack(){data=new ArrayDeque<>();} SStack(int capacity){data=new ArrayDeque<>(capacity <= 0 ? 16 : capacity);} synchronized int size(){return data.size();} synchronized boolean isEmpty(){return data.isEmpty();} synchronized void push(T x){data.addLast(x);} synchronized T pop(){return data.isEmpty()?null:data.removeLast();} synchronized T poll(){return data.pollLast();} synchronized T peek(){return data.peekLast();} synchronized void addFirst(T x){data.addFirst(x);} synchronized void addLast(T x){data.addLast(x);} synchronized T removeFirst(){return data.removeFirst();} synchronized T removeLast(){return data.removeLast();} synchronized T peekFirst(){return data.peekFirst();} synchronized T peekLast(){return data.peekLast();} synchronized List<T> snapshot(){return new ArrayList<>(data);} public synchronized Iterator<T> iterator(){return new ArrayList<>(data).iterator();} @Override public synchronized String toString(){StringBuilder b=new StringBuilder(32);b.append(\"Stack[\");boolean f=true;for(T d:data){if(!f)b.append(\", \");b.append(RT.collectionValue(d));f=false;}return b.append(']').toString();}@Override public boolean equals(Object o){return o instanceof SStack<?>s&&snapshot().equals(s.snapshot());}@Override public synchronized int hashCode(){return data.hashCode();} }");
    }

    private void emitSetRuntime(RuntimeSink sink) {
        sink.line("static final class SSet<T> implements Iterable<T> { final Set<T> data; SSet(){data=new LinkedHashSet<>();} SSet(int capacity){data=new HashSet<>(capacity < 0 ? 0 : capacity);} synchronized int size(){return data.size();} synchronized boolean isEmpty(){return data.isEmpty();} synchronized boolean add(T x){RT.rejectMutableKey(x,\"Set.add\");return data.add(x);} synchronized boolean remove(T x){return data.remove(x);} synchronized boolean contains(T x){return data.contains(x);} synchronized void clear(){data.clear();} synchronized boolean addAll(SSet<T> x){boolean changed=false;for(T v:x.snapshot())changed|=add(v);return changed;} synchronized boolean containsAll(SSet<T> x){return data.containsAll(x.snapshot());} synchronized List<T> snapshot(){return new ArrayList<>(data);} synchronized SList<T> toList(){SList<T> r=new SList<>();r.data.addAll(data);return r;} public synchronized Iterator<T> iterator(){return new ArrayList<>(data).iterator();} @Override public synchronized String toString(){StringBuilder b=new StringBuilder(32);b.append(\"Set[\");boolean f=true;for(T d:data){if(!f)b.append(\", \");b.append(RT.collectionValue(d));f=false;}return b.append(']').toString();}@Override public boolean equals(Object o){return o instanceof SSet<?>s&&snapshot().equals(s.snapshot());}@Override public synchronized int hashCode(){return data.hashCode();} }");
    }
}
