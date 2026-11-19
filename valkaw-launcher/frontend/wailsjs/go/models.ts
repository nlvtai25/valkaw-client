export namespace core {
	
	export class Config {
	    launcherVersion: string;
	    javaPath: string;
	    gameDir: string;
	    version: string;
	    profile: string;
	    gpu: string;
	    username: string;
	    memoryMB: number;
	    windowWidth: number;
	    windowHeight: number;
	    autoUpdate: boolean;
	    afterRun: string;
	    platform: string;
	    gpuPref: number;
	
	    static createFrom(source: any = {}) {
	        return new Config(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.launcherVersion = source["launcherVersion"];
	        this.javaPath = source["javaPath"];
	        this.gameDir = source["gameDir"];
	        this.version = source["version"];
	        this.profile = source["profile"];
	        this.gpu = source["gpu"];
	        this.username = source["username"];
	        this.memoryMB = source["memoryMB"];
	        this.windowWidth = source["windowWidth"];
	        this.windowHeight = source["windowHeight"];
	        this.autoUpdate = source["autoUpdate"];
	        this.afterRun = source["afterRun"];
	        this.platform = source["platform"];
	        this.gpuPref = source["gpuPref"];
	    }
	}
	export class GPU {
	    id: string;
	    name: string;
	    vendor: string;
	    address: string;
	
	    static createFrom(source: any = {}) {
	        return new GPU(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.name = source["name"];
	        this.vendor = source["vendor"];
	        this.address = source["address"];
	    }
	}
	export class Instance {
	    id: string;
	    name: string;
	    mcVersion: string;
	    loader: string;
	    loaderVersion: string;
	    javaOverride: string;
	    memoryMB: number;
	    // Go type: time
	    created: any;
	    modpackSlug?: string;
	    modpackName?: string;
	
	    static createFrom(source: any = {}) {
	        return new Instance(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.id = source["id"];
	        this.name = source["name"];
	        this.mcVersion = source["mcVersion"];
	        this.loader = source["loader"];
	        this.loaderVersion = source["loaderVersion"];
	        this.javaOverride = source["javaOverride"];
	        this.memoryMB = source["memoryMB"];
	        this.created = this.convertValues(source["created"], null);
	        this.modpackSlug = source["modpackSlug"];
	        this.modpackName = source["modpackName"];
	    }
	
		convertValues(a: any, classs: any, asMap: boolean = false): any {
		    if (!a) {
		        return a;
		    }
		    if (a.slice && a.map) {
		        return (a as any[]).map(elem => this.convertValues(elem, classs));
		    } else if ("object" === typeof a) {
		        if (asMap) {
		            for (const key of Object.keys(a)) {
		                a[key] = new classs(a[key]);
		            }
		            return a;
		        }
		        return new classs(a);
		    }
		    return a;
		}
	}
	export class Prereq {
	    java: boolean;
	    javaText: string;
	    files: boolean;
	    filesText: string;
	    graphics: boolean;
	    graphicsApi: string;
	    graphicsText: string;
	    ready: boolean;
	
	    static createFrom(source: any = {}) {
	        return new Prereq(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.java = source["java"];
	        this.javaText = source["javaText"];
	        this.files = source["files"];
	        this.filesText = source["filesText"];
	        this.graphics = source["graphics"];
	        this.graphicsApi = source["graphicsApi"];
	        this.graphicsText = source["graphicsText"];
	        this.ready = source["ready"];
	    }
	}
	export class VulkanCheck {
	    supported: boolean;
	    loader: boolean;
	    verified: boolean;
	    device: string;
	    apiVersion: string;
	    driverVersion: string;
	    reason: string;
	    fix: string;
	    fixText: string;
	    driverUrl: string;
	    summary: string;
	
	    static createFrom(source: any = {}) {
	        return new VulkanCheck(source);
	    }
	
	    constructor(source: any = {}) {
	        if ('string' === typeof source) source = JSON.parse(source);
	        this.supported = source["supported"];
	        this.loader = source["loader"];
	        this.verified = source["verified"];
	        this.device = source["device"];
	        this.apiVersion = source["apiVersion"];
	        this.driverVersion = source["driverVersion"];
	        this.reason = source["reason"];
	        this.fix = source["fix"];
	        this.fixText = source["fixText"];
	        this.driverUrl = source["driverUrl"];
	        this.summary = source["summary"];
	    }
	}

}

