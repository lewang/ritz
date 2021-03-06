(ns ritz.repl-utils.compile
  "Util functions for compilation and evaluation."
  (:import
   java.io.StringReader
   java.io.File
   java.util.zip.ZipFile
   clojure.lang.LineNumberingPushbackReader
   java.io.LineNumberReader))

(def compile-path (atom nil))

(defn- reader
  "This is a hack to get a line numbering pushback reader that
   doesn't start at line 1"
  [string line]
  (let [rdr1 (LineNumberReader. (StringReader. string))]
    (proxy [LineNumberingPushbackReader] (rdr1)
      (getLineNumber [] (+ line (.getLineNumber rdr1) -1)))))

(defn compile-region
  "Compile region."
  [string file line]
  (with-open [rdr (reader string line)]
    (clojure.lang.Compiler/load rdr file (.getName (File. file)))))

(defn eval-region
  "Evaluate string, and return the results of the last form and the last form."
  [string file line]
  ;; We can't use load, since that binds current namespace, so we would lose
  ;; namespace tracking. This is essentially clojure.lang.Compiler/load without
  ;; that namespace binding.
  (with-open [rdr (reader string line)]
    (letfn [(set-before []
              (.. clojure.lang.Compiler/LINE_BEFORE
                  (set (Integer. (.getLineNumber rdr)))))
            (set-after []
              (.. clojure.lang.Compiler/LINE_AFTER
                  (set (Integer. (.getLineNumber rdr)))))]
      ;; since these vars aren't named, we can not use `binding`
      (push-thread-bindings
       {clojure.lang.Compiler/LINE_BEFORE (Integer. line)
        clojure.lang.Compiler/LINE_AFTER (Integer. line)})
      (try
        (binding [*file* file *source-path* (.getName (File. file))]
          (loop [form (read rdr false ::eof)
                 last-form nil
                 res nil]
            (if (= form ::eof)
              [res last-form]
              (let [_ (set-after)
                    res (eval form)
                    _ (set-before)
                    next-form (read rdr false ::eof)]
                (recur next-form form res)))))
        (finally (pop-thread-bindings))))))
