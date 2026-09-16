open module org.graalvm.sl.tck {
  requires org.graalvm.polyglot;
  // truffle-api must be a named module: its PolyglotImpl extends AbstractPolyglotImpl,
  // which org.graalvm.polyglot exports only to org.graalvm.truffle.
  requires org.graalvm.truffle;
  requires org.graalvm.polyglot_tck;
  requires junit;
  
  provides org.graalvm.polyglot.tck.LanguageProvider with
    com.oracle.truffle.sl.tck.SLTCKLanguageProvider;
}
