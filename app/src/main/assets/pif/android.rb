# Android compatibility layer for Pokémon Infinite Fusion.
#
# The engine runs this before the game's own scripts (see src/config.cpp).
# Patches are applied the moment the patched class finishes loading, so the
# game's script files stay untouched and upstream game updates keep working.

module PIFAndroid
  PATCHES = {}

  def self.log(msg)
    $stderr.puts("[pif-android] #{msg}")
  rescue StandardError
  end

  # Runs the block once, right after the first `class Name ... end` body closes
  def self.after_class(name, &block)
    PATCHES[name.to_s] = block
  end

  @tracer = TracePoint.new(:end) do |tp|
    mod = tp.self
    name = mod.is_a?(Module) ? mod.name : nil
    patch = name && PATCHES.delete(name)
    if patch
      begin
        patch.call(mod)
      rescue Exception => e
        log("patch for #{name} failed: #{e.class}: #{e.message}")
      end
      @tracer.disable if PATCHES.empty?
    end
  end

  def self.start
    @tracer.enable
  end
end

# Faster startup: the game's loader (Data/Scripts.rxdata) parses and compiles
# ~640 script files on every launch. This swaps it for one that caches the
# compiled bytecode in .pif-cache/ and recompiles a file only when it changes.
# Create a file named "pif-nocache" in the game folder to turn this off.
module PIFAndroid
  CACHE_DIR = ".pif-cache/scripts"
  CACHE_FORMAT = 1

  def self.script_cache_enabled?
    !File.exist?("pif-nocache")
  end

  def self.compiled_script(file_path, name)
    stat = File.stat(file_path)
    stamp = "#{CACHE_FORMAT}|#{RUBY_VERSION}|#{file_path}|#{stat.size}|#{stat.mtime.to_i}"
    cache_file = File.join(CACHE_DIR, file_path.gsub(/[^A-Za-z0-9_.-]/, "_") + ".iseq")

    if File.exist?(cache_file)
      data = File.binread(cache_file)
      header, binary = data.split("\n", 2)
      if header == stamp && binary
        begin
          return RubyVM::InstructionSequence.load_from_binary(binary)
        rescue StandardError => e
          log("discarding bad cache for #{file_path}: #{e.message}")
        end
      end
    end

    code = File.open(file_path, "r") { |file| file.read }
    iseq = RubyVM::InstructionSequence.compile(code, name, name)
    begin
      Dir.mkdir(".pif-cache") unless File.directory?(".pif-cache")
      Dir.mkdir(CACHE_DIR) unless File.directory?(CACHE_DIR)
      File.binwrite(cache_file, stamp + "\n" + iseq.to_binary)
    rescue StandardError => e
      log("could not write cache for #{file_path}: #{e.message}")
    end
    iseq
  end

  # Same traversal order and error handling as the game's own loader
  def self.load_scripts_from_folder(path)
    files = []
    folders = []
    ignored = [".", "..", ".git", ".idea", ".gitignore"]
    Dir.foreach(path) do |f|
      next if ignored.include?(f)
      File.directory?(path + "/" + f) ? folders.push(f) : files.push(f)
    end
    files.sort!
    files.each do |f|
      begin
        compiled_script(path + "/" + f, f).eval
      rescue ScriptError
        raise ScriptError.new($!.message)
      rescue
        $!.message.sub!($!.message, traceback_report)
        raise_traceback_error
      end
    end
    folders.sort!
    folders.each { |folder| load_scripts_from_folder(path + "/" + folder) }
  end
end

# Swap in the cached loader the moment Scripts.rxdata defines its own
class Object
  def self.method_added(name)
    super
    return unless self == Object && name == :load_scripts_from_folder
    return if @pif_replacing_loader || !PIFAndroid.script_cache_enabled?
    @pif_replacing_loader = true
    define_method(:load_scripts_from_folder) { |path| PIFAndroid.load_scripts_from_folder(path) }
    private :load_scripts_from_folder
    @pif_replacing_loader = false
  end
end

# New games default to the cursor-grid text entry. The game's default is the
# keyboard screen, which on Android needs a touch keyboard. Players can still
# switch it in Options; existing saves keep their own setting.
PIFAndroid.after_class(:PokemonSystem) do |klass|
  klass.prepend(Module.new do
    def initialize(*args)
      super
      @textinput = 0
    end
  end)
end

PIFAndroid.start
